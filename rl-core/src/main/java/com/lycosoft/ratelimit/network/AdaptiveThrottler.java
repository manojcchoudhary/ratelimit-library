package com.lycosoft.ratelimit.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptive throttling mechanism for graceful performance degradation.
 * 
 * <p>Instead of immediately rejecting requests when approaching capacity,
 * this throttler injects increasing delays to smooth out traffic spikes
 * and discourage aggressive scrapers.
 * 
 * <p><b>Behavior:</b>
 * <ul>
 *   <li><b>Below soft limit:</b> No delay (full speed)</li>
 *   <li><b>Soft to hard limit:</b> Gradual delay increase</li>
 *   <li><b>Above hard limit:</b> Request rejected (429)</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * Config: softLimit=80, hardLimit=100, maxDelay=2000ms
 * 
 * Usage=50:  delay=0ms      (below soft limit)
 * Usage=80:  delay=0ms      (at soft limit)
 * Usage=90:  delay=1000ms   (50% between soft and hard)
 * Usage=95:  delay=1500ms   (75% between soft and hard)
 * Usage=100: delay=2000ms   (at hard limit)
 * Usage=101: REJECTED       (above hard limit)
 * </pre>
 * 
 * <p><b>Formula:</b>
 * <pre>
 * delay = ((usage - softLimit) / (hardLimit - softLimit)) × maxDelay
 * </pre>
 * 
 * <p><b>Benefits:</b>
 * <ul>
 *   <li>Smooths traffic spikes (better UX than hard cutoff)</li>
 *   <li>Discourages aggressive scrapers (high latency = inefficient)</li>
 *   <li>Prevents thundering herd (gradual backpressure)</li>
 *   <li>Allows legitimate bursts (temporary delays vs rejection)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class AdaptiveThrottler {
    
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThrottler.class);
    
    /**
     * Throttling strategy (linear vs exponential).
     */
    public enum Strategy {
        /** Linear delay increase: delay = ratio × maxDelay */
        LINEAR,
        
        /** Exponential delay increase: delay = (ratio²) × maxDelay */
        EXPONENTIAL
    }
    
    private final int softLimit;
    private final int hardLimit;
    private final long maxDelayMs;
    private final Strategy strategy;
    
    /**
     * Creates an adaptive throttler with default linear strategy.
     * 
     * @param softLimit the threshold where throttling begins
     * @param hardLimit the maximum allowed usage (rejection above this)
     * @param maxDelayMs the maximum delay to inject (milliseconds)
     */
    public AdaptiveThrottler(int softLimit, int hardLimit, long maxDelayMs) {
        this(softLimit, hardLimit, maxDelayMs, Strategy.LINEAR);
    }
    
    /**
     * Creates an adaptive throttler with custom strategy.
     * 
     * @param softLimit the threshold where throttling begins
     * @param hardLimit the maximum allowed usage
     * @param maxDelayMs the maximum delay to inject (milliseconds)
     * @param strategy the throttling strategy (LINEAR or EXPONENTIAL)
     */
    public AdaptiveThrottler(int softLimit, int hardLimit, long maxDelayMs, Strategy strategy) {
        if (softLimit < 0 || hardLimit <= softLimit) {
            throw new IllegalArgumentException(
                "Invalid limits: softLimit=" + softLimit + ", hardLimit=" + hardLimit +
                " (hardLimit must be > softLimit >= 0)"
            );
        }
        
        if (maxDelayMs < 0) {
            throw new IllegalArgumentException("maxDelayMs must be >= 0");
        }
        
        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
        this.maxDelayMs = maxDelayMs;
        this.strategy = strategy;
        
        logger.info("AdaptiveThrottler initialized: soft={}, hard={}, maxDelay={}ms, strategy={}", 
                   softLimit, hardLimit, maxDelayMs, strategy);
    }
    
    /**
     * Calculates the throttle delay for the given usage.
     * 
     * @param currentUsage the current usage count
     * @return the delay in milliseconds (0 if below soft limit, -1 if should reject)
     */
    public long calculateDelay(int currentUsage) {
        // Below soft limit: no delay
        if (currentUsage <= softLimit) {
            return 0;
        }
        
        // Above hard limit: reject
        if (currentUsage >= hardLimit) {
            return -1;  // Sentinel value for rejection
        }
        
        // Between soft and hard: calculate delay
        double ratio = (double) (currentUsage - softLimit) / (hardLimit - softLimit);
        
        long delay;
        switch (strategy) {
            case EXPONENTIAL:
                // Exponential: delay increases rapidly near hard limit
                delay = (long) (ratio * ratio * maxDelayMs);
                break;
                
            case LINEAR:
            default:
                // Linear: delay increases proportionally
                delay = (long) (ratio * maxDelayMs);
                break;
        }
        
        logger.trace("Throttle delay calculated: usage={}, ratio={:.2f}, delay={}ms", 
                    currentUsage, ratio, delay);
        
        return Math.min(delay, maxDelayMs);  // Cap at max delay
    }
    
    /**
     * Creates a throttle result for the given usage.
     * 
     * @param currentUsage the current usage count
     * @return the throttle result
     */
    public ThrottleResult throttle(int currentUsage) {
        long delay = calculateDelay(currentUsage);
        
        if (delay < 0) {
            // Should reject
            return ThrottleResult.reject();
        } else if (delay == 0) {
            // No throttling needed
            return ThrottleResult.allow();
        } else {
            // Apply delay
            return ThrottleResult.delay(delay);
        }
    }
    
    /**
     * Gets the soft limit threshold.
     * 
     * @return the soft limit
     */
    public int getSoftLimit() {
        return softLimit;
    }
    
    /**
     * Gets the hard limit threshold.
     * 
     * @return the hard limit
     */
    public int getHardLimit() {
        return hardLimit;
    }
    
    /**
     * Gets the maximum delay.
     * 
     * @return the max delay in milliseconds
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
    
    /**
     * Gets the throttling strategy.
     * 
     * @return the strategy
     */
    public Strategy getStrategy() {
        return strategy;
    }
    
    /**
     * Result of a throttle check.
     */
    public static class ThrottleResult {
        private final boolean allowed;
        private final long delayMs;
        
        private ThrottleResult(boolean allowed, long delayMs) {
            this.allowed = allowed;
            this.delayMs = delayMs;
        }
        
        public static ThrottleResult allow() {
            return new ThrottleResult(true, 0);
        }
        
        public static ThrottleResult delay(long delayMs) {
            return new ThrottleResult(true, delayMs);
        }
        
        public static ThrottleResult reject() {
            return new ThrottleResult(false, 0);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public long getDelayMs() {
            return delayMs;
        }
        
        public boolean shouldDelay() {
            return allowed && delayMs > 0;
        }
    }
}
