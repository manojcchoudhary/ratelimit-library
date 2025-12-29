package com.lycosoft.ratelimit.config;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for a rate limiter.
 * 
 * <p>This immutable class holds all the parameters needed to configure
 * a rate limiter, including the algorithm type, capacity, window size, etc.
 * 
 * @since 1.0.0
 */
public final class RateLimitConfig {
    
    private final String name;
    private final Algorithm algorithm;
    private final int requests;
    private final long window;
    private final TimeUnit windowUnit;
    private final FailStrategy failStrategy;
    
    // Token Bucket specific
    private final int capacity;
    private final double refillRate;
    
    // TTL for storage cleanup
    private final long ttl;
    
    private RateLimitConfig(Builder builder) {
        this.name = builder.name;
        this.algorithm = builder.algorithm;
        this.requests = builder.requests;
        this.window = builder.window;
        this.windowUnit = builder.windowUnit;
        this.failStrategy = builder.failStrategy;
        this.capacity = builder.capacity;
        this.refillRate = builder.refillRate;
        this.ttl = calculateTtl();
    }
    
    private long calculateTtl() {
        // TTL = 2 × Window Size (for cleanup)
        // Use Math.multiplyExact to detect overflow with very large windows
        try {
            long windowSeconds = windowUnit.toSeconds(window);
            return Math.multiplyExact(2L, windowSeconds);
        } catch (ArithmeticException e) {
            // If overflow occurs, use maximum safe value (Long.MAX_VALUE / 2 to avoid further overflow)
            return Long.MAX_VALUE / 2;
        }
    }
    
    public String getName() {
        return name;
    }
    
    public Algorithm getAlgorithm() {
        return algorithm;
    }
    
    public int getRequests() {
        return requests;
    }
    
    public long getWindow() {
        return window;
    }
    
    public TimeUnit getWindowUnit() {
        return windowUnit;
    }
    
    public long getWindowMillis() {
        return windowUnit.toMillis(window);
    }
    
    public FailStrategy getFailStrategy() {
        return failStrategy;
    }
    
    public int getCapacity() {
        return capacity;
    }
    
    public double getRefillRate() {
        return refillRate;
    }
    
    public long getTtl() {
        return ttl;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private Algorithm algorithm = Algorithm.TOKEN_BUCKET;
        private int requests;
        private long window;
        private TimeUnit windowUnit = TimeUnit.SECONDS;
        private FailStrategy failStrategy = FailStrategy.FAIL_OPEN;
        private int capacity;
        private double refillRate;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder algorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }
        
        public Builder requests(int requests) {
            this.requests = requests;
            return this;
        }
        
        public Builder window(long window) {
            this.window = window;
            return this;
        }
        
        public Builder windowUnit(TimeUnit windowUnit) {
            this.windowUnit = windowUnit;
            return this;
        }
        
        public Builder failStrategy(FailStrategy failStrategy) {
            this.failStrategy = failStrategy;
            return this;
        }
        
        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }
        
        public Builder refillRate(double refillRate) {
            this.refillRate = refillRate;
            return this;
        }
        
        public RateLimitConfig build() {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(algorithm, "algorithm cannot be null");
            Objects.requireNonNull(windowUnit, "windowUnit cannot be null");
            Objects.requireNonNull(failStrategy, "failStrategy cannot be null");
            
            if (requests <= 0) {
                throw new IllegalArgumentException("requests must be positive");
            }
            if (window <= 0) {
                throw new IllegalArgumentException("window must be positive");
            }
            
            // Auto-calculate Token Bucket parameters if not set
            if (algorithm == Algorithm.TOKEN_BUCKET) {
                if (capacity == 0) {
                    capacity = requests; // Default: capacity = requests
                }
                if (refillRate == 0.0) {
                    // Default: refill rate = requests per window
                    refillRate = (double) requests / windowUnit.toMillis(window);
                }
            }
            
            return new RateLimitConfig(this);
        }
    }
    
    /**
     * Rate limiting algorithm type.
     */
    public enum Algorithm {
        /**
         * Token Bucket algorithm - allows smooth bursts.
         * Good for: APIs that need burst capacity.
         */
        TOKEN_BUCKET,
        
        /**
         * Sliding Window Counter algorithm - high accuracy.
         * Good for: Strict rate limiting without burst allowance.
         */
        SLIDING_WINDOW
    }
    
    /**
     * Failure strategy when storage provider is unavailable.
     */
    public enum FailStrategy {
        /**
         * Fail Open (AP mode) - Allow requests when storage fails.
         * Prioritizes availability over strict enforcement.
         */
        FAIL_OPEN,
        
        /**
         * Fail Closed (CP mode) - Deny requests when storage fails.
         * Prioritizes consistency over availability.
         */
        FAIL_CLOSED
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitConfig that = (RateLimitConfig) o;
        return requests == that.requests &&
                window == that.window &&
                capacity == that.capacity &&
                Double.compare(that.refillRate, refillRate) == 0 &&
                ttl == that.ttl &&
                Objects.equals(name, that.name) &&
                algorithm == that.algorithm &&
                windowUnit == that.windowUnit &&
                failStrategy == that.failStrategy;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, algorithm, requests, window, windowUnit, 
                           failStrategy, capacity, refillRate, ttl);
    }
    
    @Override
    public String toString() {
        return "RateLimitConfig{" +
                "name='" + name + '\'' +
                ", algorithm=" + algorithm +
                ", requests=" + requests +
                ", window=" + window +
                ", windowUnit=" + windowUnit +
                ", failStrategy=" + failStrategy +
                ", capacity=" + capacity +
                ", refillRate=" + refillRate +
                ", ttl=" + ttl +
                '}';
    }
}
