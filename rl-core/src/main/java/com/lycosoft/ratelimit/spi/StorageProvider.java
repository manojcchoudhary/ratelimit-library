package com.lycosoft.ratelimit.spi;

import com.lycosoft.ratelimit.config.RateLimitConfig;

import java.util.Map;
import java.util.Optional;

/**
 * Service Provider Interface for rate limit storage.
 * 
 * <p>Implementations provide pluggable storage backends (Redis, Hazelcast, Caffeine, etc.)
 * for maintaining rate limit state across requests.
 * 
 * <p><b>Thread Safety:</b> Implementations must be thread-safe.
 * 
 * <p><b>Clock Synchronization:</b> The {@link #getCurrentTime()} method is critical
 * for distributed consistency. Redis implementations should use REDIS.TIME() command,
 * while local implementations can use System.currentTimeMillis().
 * 
 * @since 1.0.0
 */
public interface StorageProvider {
    
    /**
     * Returns the current time in milliseconds.
     * 
     * <p>For distributed providers (Redis, Hazelcast):
     * <ul>
     *   <li>Returns the storage system's clock</li>
     *   <li>Ensures cluster-wide time consistency</li>
     *   <li>Prevents window fragmentation due to clock skew</li>
     * </ul>
     * 
     * <p>For local providers (Caffeine, in-memory):
     * <ul>
     *   <li>Returns System.currentTimeMillis()</li>
     *   <li>No cross-node coordination needed</li>
     * </ul>
     * 
     * @return current time in milliseconds since epoch
     */
    long getCurrentTime();
    
    /**
     * Attempts to acquire permission to proceed with a request based on the rate limit configuration.
     * 
     * <p>This method must be atomic and thread-safe. For Redis implementations, use Lua scripts
     * to ensure atomicity of read-calculate-write operations.
     * 
     * @param key the unique identifier for this rate limiter (e.g., "user:123:/api/orders")
     * @param config the rate limit configuration
     * @param currentTime the current time in milliseconds (from {@link #getCurrentTime()})
     * @return true if the request is allowed, false if rate limit exceeded
     */
    boolean tryAcquire(String key, RateLimitConfig config, long currentTime);
    
    /**
     * Resets the rate limit state for the given key.
     * 
     * <p>This is useful for testing or administrative operations.
     * 
     * @param key the unique identifier for this rate limiter
     */
    void reset(String key);
    
    /**
     * Retrieves the current state of the rate limiter for the given key.
     * 
     * <p>This is primarily used for observability and debugging.
     * 
     * @param key the unique identifier for this rate limiter
     * @return the current state, or empty if no state exists
     */
    Optional<RateLimitState> getState(String key);

    /**
     * Checks if the storage provider is healthy and ready to serve requests.
     *
     * @return true if healthy, false otherwise
     */
    default boolean isHealthy() {
        return true; // Default: assume healthy
    };

    /**
     * Gets diagnostic information about the storage provider.
     * Used for health checks and monitoring.
     *
     * @return map of diagnostic keys and values
     */
    default Map<String, Object> getDiagnostics() {
        return Map.of("type", getClass().getSimpleName());
    }
}
