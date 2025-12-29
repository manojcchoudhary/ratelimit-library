package com.lycosoft.ratelimit.engine;

import com.lycosoft.ratelimit.http.RateLimitProblemDetail;

/**
 * Represents the decision made by a rate limiter.
 * 
 * <p>This immutable value object contains all information about whether
 * a request was allowed or denied, along with metadata for client headers.
 * 
 * <p><b>New in 1.1.0:</b>
 * <ul>
 *   <li>Adaptive throttling delay support</li>
 *   <li>RFC 9457 Problem Details integration</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public final class RateLimitDecision {
    
    private final boolean allowed;
    private final String limiterName;
    private final int limit;
    private final int remaining;
    private final long resetTime;
    private final String reason;
    private final long delayMs;  // NEW: Adaptive throttling delay
    private final RateLimitProblemDetail problemDetail;  // NEW: RFC 9457 support
    
    private RateLimitDecision(Builder builder) {
        this.allowed = builder.allowed;
        this.limiterName = builder.limiterName;
        this.limit = builder.limit;
        this.remaining = builder.remaining;
        this.resetTime = builder.resetTime;
        this.reason = builder.reason;
        this.delayMs = builder.delayMs;
        this.problemDetail = builder.problemDetail;
    }
    
    /**
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed() {
        return allowed;
    }
    
    /**
     * @return the name of the rate limiter that made this decision
     */
    public String getLimiterName() {
        return limiterName;
    }
    
    /**
     * @return the configured rate limit (e.g., 100 requests)
     */
    public int getLimit() {
        return limit;
    }
    
    /**
     * @return the remaining capacity (e.g., 42 requests remaining)
     */
    public int getRemaining() {
        return remaining;
    }
    
    /**
     * @return the time when the limit will reset (milliseconds since epoch)
     */
    public long getResetTime() {
        return resetTime;
    }
    
    /**
     * @return the reason for denial (if denied), or null if allowed
     */
    public String getReason() {
        return reason;
    }
    
    /**
     * Gets the adaptive throttling delay.
     * 
     * <p>If this value is greater than 0, the interceptor should delay
     * the request by this many milliseconds before proceeding.
     * 
     * @return delay in milliseconds (0 = no delay)
     * @since 1.1.0
     */
    public long getDelayMs() {
        return delayMs;
    }
    
    /**
     * Gets the RFC 9457 Problem Detail for this decision.
     * 
     * <p>This is null for allowed requests, and populated for denied requests
     * when RFC 9457 support is enabled.
     * 
     * @return the problem detail, or null
     * @since 1.1.0
     */
    public RateLimitProblemDetail getProblemDetail() {
        return problemDetail;
    }
    
    /**
     * @return seconds until reset (for Retry-After header)
     */
    public long getRetryAfterSeconds() {
        if (resetTime <= 0) {
            return 0;
        }
        long secondsUntilReset = (resetTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, secondsUntilReset);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates an ALLOWED decision.
     */
    public static RateLimitDecision allow(String limiterName, int limit, int remaining, long resetTime) {
        return builder()
            .allowed(true)
            .limiterName(limiterName)
            .limit(limit)
            .remaining(remaining)
            .resetTime(resetTime)
            .build();
    }
    
    /**
     * Creates a DENIED decision.
     */
    public static RateLimitDecision deny(String limiterName, int limit, long resetTime, String reason) {
        return builder()
            .allowed(false)
            .limiterName(limiterName)
            .limit(limit)
            .remaining(0)
            .resetTime(resetTime)
            .reason(reason)
            .build();
    }
    
    public static class Builder {
        private boolean allowed;
        private String limiterName;
        private int limit;
        private int remaining;
        private long resetTime;
        private String reason;
        private long delayMs = 0;  // NEW
        private RateLimitProblemDetail problemDetail;  // NEW
        
        public Builder allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }
        
        public Builder limiterName(String limiterName) {
            this.limiterName = limiterName;
            return this;
        }
        
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }
        
        public Builder remaining(int remaining) {
            this.remaining = remaining;
            return this;
        }
        
        public Builder resetTime(long resetTime) {
            this.resetTime = resetTime;
            return this;
        }
        
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }
        
        /**
         * Sets the adaptive throttling delay.
         * 
         * @param delayMs delay in milliseconds
         * @return this builder
         * @since 1.1.0
         */
        public Builder delayMs(long delayMs) {
            this.delayMs = delayMs;
            return this;
        }
        
        /**
         * Sets the RFC 9457 Problem Detail.
         * 
         * @param problemDetail the problem detail
         * @return this builder
         * @since 1.1.0
         */
        public Builder problemDetail(RateLimitProblemDetail problemDetail) {
            this.problemDetail = problemDetail;
            return this;
        }
        
        public RateLimitDecision build() {
            return new RateLimitDecision(this);
        }
    }
    
    @Override
    public String toString() {
        return "RateLimitDecision{" +
                "allowed=" + allowed +
                ", limiterName='" + limiterName + '\'' +
                ", limit=" + limit +
                ", remaining=" + remaining +
                ", resetTime=" + resetTime +
                ", reason='" + reason + '\'' +
                ", delayMs=" + delayMs +
                '}';
    }
}
