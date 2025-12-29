package com.lycosoft.ratelimit.throttle;

/**
 * Calculates adaptive throttling delays for graceful degradation.
 * 
 * <p>Adaptive throttling allows the system to slow down traffic before completely
 * blocking it, providing a better user experience and discouraging aggressive scrapers.
 * 
 * <p><b>Concept:</b>
 * <pre>
 * Soft Limit: 80 requests  (throttling begins)
 * Hard Limit: 100 requests (complete blocking)
 * Max Delay:  2000ms       (maximum injected delay)
 * 
 * At 80 requests:  delay = 0ms    (soft limit)
 * At 90 requests:  delay = 1000ms (50% between soft and hard)
 * At 100 requests: delay = 2000ms (hard limit) → then block
 * </pre>
 * 
 * <p><b>Delay Calculation Strategies:</b>
 * <ul>
 *   <li><b>Linear:</b> Delay increases proportionally</li>
 *   <li><b>Exponential:</b> Delay increases exponentially (more aggressive)</li>
 * </ul>
 * 
 * <p><b>Formula (Linear):</b>
 * <pre>
 * delay = ((usage - softLimit) / (hardLimit - softLimit)) × maxDelay
 * </pre>
 * 
 * <p><b>Formula (Exponential):</b>
 * <pre>
 * ratio = (usage - softLimit) / (hardLimit - softLimit)
 * delay = (ratio²) × maxDelay
 * </pre>
 * 
 * <p><b>Thread Safety:</b>
 * This class is stateless and thread-safe. All calculations are based on
 * provided parameters with no shared mutable state.
 * 
 * @since 1.1.0
 */
public class AdaptiveThrottleCalculator {
    
    /**
     * Throttle strategy for delay calculation.
     */
    public enum Strategy {
        /**
         * Linear delay progression.
         * 
         * <p>Provides smooth, predictable degradation.
         * Best for user-facing APIs where predictability matters.
         */
        LINEAR,
        
        /**
         * Exponential delay progression.
         * 
         * <p>Applies minimal delay initially, then aggressive delays near hard limit.
         * Best for discouraging aggressive automation/scraping.
         */
        EXPONENTIAL
    }
    
    /**
     * Configuration for adaptive throttling.
     */
    public static class ThrottleConfig {
        private final int softLimit;
        private final int hardLimit;
        private final long maxDelayMs;
        private final Strategy strategy;
        
        /**
         * Creates a throttle configuration.
         * 
         * @param softLimit usage threshold where throttling begins (e.g., 80)
         * @param hardLimit usage threshold where blocking begins (e.g., 100)
         * @param maxDelayMs maximum delay to inject in milliseconds
         * @param strategy delay calculation strategy
         */
        public ThrottleConfig(int softLimit, int hardLimit, long maxDelayMs, Strategy strategy) {
            if (softLimit >= hardLimit) {
                throw new IllegalArgumentException("softLimit must be < hardLimit");
            }
            if (softLimit < 0 || hardLimit <= 0) {
                throw new IllegalArgumentException("Limits must be positive");
            }
            if (maxDelayMs <= 0) {
                throw new IllegalArgumentException("maxDelayMs must be positive");
            }
            
            this.softLimit = softLimit;
            this.hardLimit = hardLimit;
            this.maxDelayMs = maxDelayMs;
            this.strategy = strategy;
        }
        
        /**
         * Creates a linear throttle configuration.
         * 
         * @param softLimit usage threshold where throttling begins
         * @param hardLimit usage threshold where blocking begins
         * @param maxDelayMs maximum delay in milliseconds
         * @return the configuration
         */
        public static ThrottleConfig linear(int softLimit, int hardLimit, long maxDelayMs) {
            return new ThrottleConfig(softLimit, hardLimit, maxDelayMs, Strategy.LINEAR);
        }
        
        /**
         * Creates an exponential throttle configuration.
         * 
         * @param softLimit usage threshold where throttling begins
         * @param hardLimit usage threshold where blocking begins
         * @param maxDelayMs maximum delay in milliseconds
         * @return the configuration
         */
        public static ThrottleConfig exponential(int softLimit, int hardLimit, long maxDelayMs) {
            return new ThrottleConfig(softLimit, hardLimit, maxDelayMs, Strategy.EXPONENTIAL);
        }
        
        public int getSoftLimit() {
            return softLimit;
        }
        
        public int getHardLimit() {
            return hardLimit;
        }
        
        public long getMaxDelayMs() {
            return maxDelayMs;
        }
        
        public Strategy getStrategy() {
            return strategy;
        }
    }
    
    /**
     * Calculates the throttle delay for a given usage.
     * 
     * <p><b>Examples (Linear):</b>
     * <pre>
     * Config: softLimit=80, hardLimit=100, maxDelay=2000ms
     * 
     * calculateDelay(75, config) = 0ms     (below soft limit)
     * calculateDelay(80, config) = 0ms     (at soft limit)
     * calculateDelay(90, config) = 1000ms  (50% of range)
     * calculateDelay(100, config) = 2000ms (at hard limit)
     * calculateDelay(105, config) = 2000ms (above hard limit, capped)
     * </pre>
     * 
     * <p><b>Examples (Exponential):</b>
     * <pre>
     * Config: softLimit=80, hardLimit=100, maxDelay=2000ms
     * 
     * calculateDelay(80, config) = 0ms     (at soft limit)
     * calculateDelay(85, config) = 125ms   (25% → 6.25% of max)
     * calculateDelay(90, config) = 500ms   (50% → 25% of max)
     * calculateDelay(95, config) = 1125ms  (75% → 56.25% of max)
     * calculateDelay(100, config) = 2000ms (100% → 100% of max)
     * </pre>
     * 
     * @param currentUsage the current usage count
     * @param config the throttle configuration
     * @return delay in milliseconds (0 if below soft limit, capped at maxDelay)
     */
    public static long calculateDelay(int currentUsage, ThrottleConfig config) {
        // Below soft limit: no throttling
        if (currentUsage <= config.softLimit) {
            return 0;
        }
        
        // Above hard limit: maximum delay (though usually request should be denied)
        if (currentUsage >= config.hardLimit) {
            return config.maxDelayMs;
        }
        
        // Calculate ratio: 0.0 at soft limit, 1.0 at hard limit
        double usageRange = config.hardLimit - config.softLimit;
        double usageAboveSoft = currentUsage - config.softLimit;
        double ratio = usageAboveSoft / usageRange;
        
        // Apply strategy
        double delayRatio = switch (config.strategy) {
            case LINEAR -> ratio;
            case EXPONENTIAL -> ratio * ratio;  // Quadratic progression
        };
        
        // Calculate final delay
        long delay = (long) (delayRatio * config.maxDelayMs);
        
        // Ensure delay is within bounds
        return Math.max(0, Math.min(delay, config.maxDelayMs));
    }
    
    /**
     * Checks if throttling should be applied for the given usage.
     * 
     * @param currentUsage the current usage count
     * @param config the throttle configuration
     * @return true if usage is between soft and hard limits
     */
    public static boolean shouldThrottle(int currentUsage, ThrottleConfig config) {
        return currentUsage > config.softLimit && currentUsage < config.hardLimit;
    }
    
    /**
     * Checks if the usage has exceeded the hard limit.
     * 
     * @param currentUsage the current usage count
     * @param config the throttle configuration
     * @return true if usage >= hard limit (should block)
     */
    public static boolean shouldBlock(int currentUsage, ThrottleConfig config) {
        return currentUsage >= config.hardLimit;
    }
}
