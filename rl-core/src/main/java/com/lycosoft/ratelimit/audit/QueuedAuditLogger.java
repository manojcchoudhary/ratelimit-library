package com.lycosoft.ratelimit.audit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lycosoft.ratelimit.spi.AuditLogger;
import com.lycosoft.ratelimit.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple asynchronous audit logger using a blocking queue.
 * 
 * <p>This implementation uses a dedicated background thread to process
 * audit events, ensuring that logging never blocks the request path.
 * 
 * <p><b>Performance:</b> Queue offer is non-blocking (<1μs).
 * 
 * <p><b>Capacity:</b> Default queue size is 10,000 events. If the queue fills up,
 * new events are dropped and a warning is logged.
 * 
 * @since 1.0.0
 */
public class QueuedAuditLogger implements AuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(QueuedAuditLogger.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    private final BlockingQueue<AuditEvent> queue;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final int queueCapacity;

    private final AtomicLong lastFullWarning = new AtomicLong(0);
    
    /**
     * Creates a queued audit logger with default capacity (10,000).
     */
    public QueuedAuditLogger() {
        this(10_000);
    }
    
    /**
     * Creates a queued audit logger with specified capacity.
     * 
     * @param queueCapacity the maximum queue size
     */
    public QueuedAuditLogger(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.running = new AtomicBoolean(true);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audit-logger");
            t.setDaemon(true);
            return t;
        });
        
        // Start consumer thread
        executor.submit(this::consumeEvents);
        logger.info("QueuedAuditLogger started with capacity: {}", queueCapacity);
    }
    
    @Override
    public void logConfigChange(ConfigChangeEvent event) {
        enqueueEvent(new AuditEvent(AuditEventType.CONFIG_CHANGE, event));
    }
    
    @Override
    public void logEnforcementAction(EnforcementEvent event) {
        enqueueEvent(new AuditEvent(AuditEventType.ENFORCEMENT, event));
    }
    
    @Override
    public void logSystemFailure(SystemFailureEvent event) {
        enqueueEvent(new AuditEvent(AuditEventType.SYSTEM_FAILURE, event));
    }
    
    /**
     * Enqueues an event for async processing.
     * 
     * @param event the event to enqueue
     */
    private void enqueueEvent(AuditEvent event) {
        if (!running.get()) {
            logger.warn("Audit logger is shut down, event dropped: {}", event.getType());
            return;
        }
        
        // Non-blocking offer - drops if queue full
        if (!queue.offer(event)) {
            long now = System.currentTimeMillis();
            long last = lastFullWarning.get();
            // Log at most once per second
            if (now - last > 1000 && lastFullWarning.compareAndSet(last, now)) {
                logger.warn("Audit queue full. Events are being dropped. Event capacity: {} , event dropped: {}", queueCapacity, event.getType());
            }
        }
    }
    
    /**
     * Consumer thread that processes queued events.
     */
    private void consumeEvents() {
        logger.info("Audit logger consumer thread started");
        
        while (running.get() || !queue.isEmpty()) {
            try {
                // Blocking take with timeout
                AuditEvent event = queue.poll(1, TimeUnit.SECONDS);
                
                if (event != null) {
                    processEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Audit logger consumer thread interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error processing audit event", e);
                // Continue processing despite errors
            }
        }
        
        logger.info("Audit logger consumer thread stopped");
    }
    
    /**
     * Processes a single audit event.
     * 
     * @param event the event to process
     */
    private void processEvent(AuditEvent event) {
        try {
            String json = eventToJson(event);
            auditLog.info(json);
        } catch (Exception e) {
            logger.error("Failed to serialize audit event: {}", event.getType(), e);
        }
    }
    
    /**
     * Converts an audit event to JSON string.
     * 
     * @param event the event
     * @return JSON string
     */
    private String eventToJson(AuditEvent event) {
        return JsonUtil.gson().toJson(event);
    }
    
    /**
     * Shuts down the audit logger gracefully.
     * 
     * <p>This method blocks until all queued events are processed or timeout occurs.
     * 
     * @param timeoutSeconds the maximum time to wait for shutdown
     */
    public void shutdown(int timeoutSeconds) {
        logger.info("Shutting down audit logger (timeout: {}s)...", timeoutSeconds);
        running.set(false);
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("Audit logger did not terminate within {}s, forcing shutdown", timeoutSeconds);
                executor.shutdownNow();
            } else {
                logger.info("Audit logger shut down gracefully");
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for audit logger shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Gets the current queue size.
     * 
     * @return number of events waiting to be processed
     */
    public int getQueueSize() {
        return queue.size();
    }
    
    /**
     * Checks if the audit logger is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Internal wrapper for audit events.
     */
    private static class AuditEvent {
        private final AuditEventType type;
        private final Object payload;
        private final long timestamp;
        
        AuditEvent(AuditEventType type, Object payload) {
            this.type = type;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }
        
        public AuditEventType getType() {
            return type;
        }
        
        public Object getPayload() {
            return payload;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * Audit event types.
     */
    private enum AuditEventType {
        CONFIG_CHANGE,
        ENFORCEMENT,
        SYSTEM_FAILURE
    }
}
