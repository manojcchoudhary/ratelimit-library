package com.lycosoft.ratelimit.spi;

/**
 * Represents the current state of a rate limiter.
 */
public interface RateLimitState {
    /**
     * @return the configured limit (e.g., 100 requests)
     */
    int getLimit();

    /**
     * @return the remaining capacity (e.g., 42 requests remaining)
     */
    int getRemaining();

    /**
     * @return the time when the limit will reset (milliseconds since epoch)
     */
    long getResetTime();

    /**
     * @return the current usage count
     */
    int getCurrentUsage();
}
