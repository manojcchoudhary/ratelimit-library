package com.lycosoft.ratelimit.audit;

import com.lycosoft.ratelimit.spi.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audit logger with intelligent sampling to prevent log saturation during DDoS attacks.
 * 
 * <p>This implementation addresses <b>Pre-flight Check #4: Audit Log Saturation</b>.
 * 
 * <p><b>Problem:</b>
 * On a high-traffic API under DDoS attack, enforcement audits could generate
 * gigabytes of logs per minute, overwhelming logging infrastructure and filling disks.
 * 
 * <p><b>Solution:</b>
 * <ul>
 *   <li><b>Rate-based sampling:</b> Log first N events per minute, then sample</li>
 *   <li><b>Summary aggregation:</b> Emit periodic summaries instead of individual events</li>
 *   <li><b>Threshold suppression:</b> Stop logging when saturation threshold reached</li>
 * </ul>
 * 
 * <p><b>Example Behavior:</b>
 * <pre>
 * Normal load (100 events/min):  Log all events
 * High load (1000 events/min):   Log first 100, then every 10th, emit summary
 * DDoS attack (100K events/min): Log first 100, emit summaries every 60s
 * 
 * Result: Logs capped at ~100 events + 1 summary/min instead of 100K events/min
 * </pre>
 * 
 * @since 1.0.0
 */
public class SampledAuditLogger implements AuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(SampledAuditLogger.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    /**
     * Maximum events to log in detail per minute before switching to sampling.
     */
    private final int maxEventsPerMinute;
    
    /**
     * Interval (in seconds) for emitting summary statistics.
     */
    private final int summaryIntervalSeconds;
    
    /**
     * Sampling rate after threshold exceeded (e.g., 10 = log every 10th event).
     */
    private final int samplingRate;
    
    /**
     * Delegate logger for actual logging.
     */
    private final AuditLogger delegate;
    
    /**
     * Event counters per limiter: limiterName -> EventCounter
     */
    private final Map<String, EventCounter> eventCounters;
    
    /**
     * Last summary timestamp.
     */
    private final AtomicLong lastSummaryTime;
    
    /**
     * Creates a sampled audit logger with default settings.
     * 
     * <p>Defaults:
     * <ul>
     *   <li>Max events per minute: 100</li>
     *   <li>Summary interval: 60 seconds</li>
     *   <li>Sampling rate: 10 (every 10th event)</li>
     * </ul>
     * 
     * @param delegate the underlying audit logger
     */
    public SampledAuditLogger(AuditLogger delegate) {
        this(delegate, 100, 60, 10);
    }
    
    /**
     * Creates a sampled audit logger with custom settings.
     * 
     * @param delegate the underlying audit logger
     * @param maxEventsPerMinute maximum detailed events per minute
     * @param summaryIntervalSeconds interval for summary emission
     * @param samplingRate sampling rate after threshold (e.g., 10 = every 10th)
     */
    public SampledAuditLogger(AuditLogger delegate, 
                             int maxEventsPerMinute,
                             int summaryIntervalSeconds,
                             int samplingRate) {
        this.delegate = delegate;
        this.maxEventsPerMinute = maxEventsPerMinute;
        this.summaryIntervalSeconds = summaryIntervalSeconds;
        this.samplingRate = samplingRate;
        this.eventCounters = new ConcurrentHashMap<>();
        this.lastSummaryTime = new AtomicLong(System.currentTimeMillis());
        
        logger.info("SampledAuditLogger initialized: maxEvents={}/min, summaryInterval={}s, samplingRate=1/{}", 
                   maxEventsPerMinute, summaryIntervalSeconds, samplingRate);
    }
    
    @Override
    public void logConfigChange(ConfigChangeEvent event) {
        // Config changes are always logged (low volume, high importance)
        delegate.logConfigChange(event);
    }
    
    @Override
    public void logEnforcementAction(EnforcementEvent event) {
        String limiterName = event.getLimiterName();
        EventCounter counter = eventCounters.computeIfAbsent(limiterName, k -> new EventCounter());
        
        // Increment total count
        long totalCount = counter.totalEvents.incrementAndGet();
        long minuteCount = counter.eventsThisMinute.incrementAndGet();
        
        // Check if we should emit a summary
        checkAndEmitSummary();
        
        // Decide whether to log this event
        if (shouldLogEvent(minuteCount, totalCount)) {
            delegate.logEnforcementAction(event);
            counter.loggedEvents.incrementAndGet();
        } else {
            counter.suppressedEvents.incrementAndGet();
        }
    }
    
    @Override
    public void logSystemFailure(SystemFailureEvent event) {
        // System failures are always logged (critical events)
        delegate.logSystemFailure(event);
    }
    
    /**
     * Determines whether an event should be logged based on current load.
     * 
     * @param minuteCount events this minute
     * @param totalCount total events since startup
     * @return true if the event should be logged
     */
    private boolean shouldLogEvent(long minuteCount, long totalCount) {
        // Phase 1: Log all events (under threshold)
        if (minuteCount <= maxEventsPerMinute) {
            return true;
        }
        
        // Phase 2: Sample events (over threshold)
        // Log every Nth event to reduce volume
        return totalCount % samplingRate == 0;
    }
    
    /**
     * Checks if it's time to emit a summary and does so if needed.
     */
    private void checkAndEmitSummary() {
        long now = System.currentTimeMillis();
        long lastSummary = lastSummaryTime.get();
        long elapsed = now - lastSummary;
        
        if (elapsed >= summaryIntervalSeconds * 1000) {
            if (lastSummaryTime.compareAndSet(lastSummary, now)) {
                emitSummary();
            }
        }
    }
    
    /**
     * Emits a summary of event statistics and resets minute counters.
     */
    private void emitSummary() {
        logger.info("=== Rate Limit Audit Summary ({}s interval) ===", summaryIntervalSeconds);
        
        for (Map.Entry<String, EventCounter> entry : eventCounters.entrySet()) {
            String limiterName = entry.getKey();
            EventCounter counter = entry.getValue();
            
            long total = counter.totalEvents.get();
            long logged = counter.loggedEvents.get();
            long suppressed = counter.suppressedEvents.get();
            long thisMinute = counter.eventsThisMinute.getAndSet(0); // Reset minute counter
            
            if (thisMinute > 0) {
                auditLog.info("SUMMARY: limiter={}, total={}, logged={}, suppressed={}, lastMinute={}", 
                             limiterName, total, logged, suppressed, thisMinute);
                
                // Warn if heavy suppression
                if (suppressed > logged * 10) {
                    logger.warn("Heavy audit suppression for limiter '{}': {}% of events suppressed",
                               limiterName, (suppressed * 100 / total));
                }
            }
        }
    }
    
    /**
     * Gets statistics for a specific limiter.
     * 
     * @param limiterName the limiter name
     * @return the event counter, or null if not found
     */
    public EventCounter getStats(String limiterName) {
        return eventCounters.get(limiterName);
    }
    
    /**
     * Resets all counters (useful for testing).
     */
    public void resetCounters() {
        eventCounters.clear();
        lastSummaryTime.set(System.currentTimeMillis());
        logger.info("Reset all event counters");
    }
    
    /**
     * Event counter for tracking statistics per limiter.
     */
    public static class EventCounter {
        private final AtomicLong totalEvents = new AtomicLong(0);
        private final AtomicLong loggedEvents = new AtomicLong(0);
        private final AtomicLong suppressedEvents = new AtomicLong(0);
        private final AtomicInteger eventsThisMinute = new AtomicInteger(0);
        
        public long getTotalEvents() {
            return totalEvents.get();
        }
        
        public long getLoggedEvents() {
            return loggedEvents.get();
        }
        
        public long getSuppressedEvents() {
            return suppressedEvents.get();
        }
        
        public int getEventsThisMinute() {
            return eventsThisMinute.get();
        }
        
        public double getSuppressionRate() {
            long total = totalEvents.get();
            return total > 0 ? (double) suppressedEvents.get() / total : 0.0;
        }
    }
}
