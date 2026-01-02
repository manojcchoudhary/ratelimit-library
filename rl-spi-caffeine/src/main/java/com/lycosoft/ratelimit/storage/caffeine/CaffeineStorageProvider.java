package com.lycosoft.ratelimit.storage.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lycosoft.ratelimit.algorithm.FixedWindowAlgorithm;
import com.lycosoft.ratelimit.algorithm.SlidingWindowAlgorithm;
import com.lycosoft.ratelimit.algorithm.TokenBucketAlgorithm;
import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caffeine-based in-memory storage provider for rate limiting.
 * 
 * <p>This implementation provides:
 * <ul>
 *   <li>Local (single-node) rate limiting</li>
 *   <li>L2 fallback when Redis is unavailable</li>
 *   <li>High performance (no network overhead)</li>
 *   <li>TTL-based automatic cleanup</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> Caffeine is thread-safe and lock-free for high concurrency.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Testing and development</li>
 *   <li>Single-node deployments</li>
 *   <li>L2 fallback in tiered storage</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class CaffeineStorageProvider implements StorageProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(CaffeineStorageProvider.class);
    
    /**
     * Cache for Token Bucket states.
     */
    private final Cache<String, TokenBucketAlgorithm.BucketState> tokenBucketCache;

    /**
     * Cache for Sliding Window states.
     */
    private final Cache<String, SlidingWindowAlgorithm.WindowState> slidingWindowCache;

    /**
     * Cache for Fixed Window states.
     */
    private final Cache<String, FixedWindowAlgorithm.WindowState> fixedWindowCache;

    /**
     * Cache for algorithm configurations (to know which cache to use).
     */
    private final Cache<String, RateLimitConfig.Algorithm> algorithmCache;
    
    /**
     * Creates a Caffeine storage provider with default settings.
     * 
     * <p>Default: max 10,000 entries, TTL=2 hours
     */
    public CaffeineStorageProvider() {
        this(10_000, 2, TimeUnit.HOURS);
    }
    
    /**
     * Creates a Caffeine storage provider with custom settings.
     * 
     * @param maxEntries the maximum number of entries
     * @param ttlDuration the TTL duration
     * @param ttlUnit the TTL time unit
     */
    public CaffeineStorageProvider(long maxEntries, long ttlDuration, TimeUnit ttlUnit) {
        this.tokenBucketCache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttlDuration, ttlUnit)
                .recordStats()
                .build();

        this.slidingWindowCache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttlDuration, ttlUnit)
                .recordStats()
                .build();

        this.fixedWindowCache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttlDuration, ttlUnit)
                .recordStats()
                .build();

        this.algorithmCache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttlDuration, ttlUnit)
                .build();

        logger.info("CaffeineStorageProvider initialized (maxEntries={}, TTL={}{})",
                   maxEntries, ttlDuration, ttlUnit);
    }
    
    @Override
    public long getCurrentTime() {
        // Local provider uses system time
        return System.currentTimeMillis();
    }
    
    @Override
    public boolean tryAcquire(String key, RateLimitConfig config, long currentTime) {
        // Store algorithm type for this key
        algorithmCache.put(key, config.getAlgorithm());

        return switch (config.getAlgorithm()) {
            case TOKEN_BUCKET -> tryAcquireTokenBucket(key, config, currentTime);
            case SLIDING_WINDOW -> tryAcquireSlidingWindow(key, config, currentTime);
            case FIXED_WINDOW -> tryAcquireFixedWindow(key, config, currentTime);
        };
    }
    
    /**
     * Tries to acquire using Token Bucket algorithm.
     *
     * <p><b>Thread Safety:</b> Uses atomic compute() operation to prevent
     * race conditions (TOCTOU - Time-of-Check to Time-of-Use).
     *
     * @param key the rate limit key
     * @param config the configuration
     * @param currentTime the current time
     * @return true if allowed, false otherwise
     */
    private boolean tryAcquireTokenBucket(String key, RateLimitConfig config, long currentTime) {
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(
            config.getCapacity(),
            config.getRefillRate()
        );

        // Use atomic compute() to prevent race conditions (TOCTOU)
        // Always update state to track refill time correctly, regardless of allow/deny
        AtomicBoolean allowed = new AtomicBoolean(false);
        tokenBucketCache.asMap().compute(key, (k, oldState) -> {
            TokenBucketAlgorithm.BucketState newState = algorithm.tryConsume(oldState, 1, currentTime);
            allowed.set(newState.allowed());
            return newState;  // Always persist state for correct refill tracking
        });

        logger.trace("Token Bucket check for key={}, allowed={}", key, allowed.get());

        return allowed.get();
    }
    
    /**
     * Tries to acquire using Sliding Window algorithm.
     *
     * <p><b>Thread Safety:</b> Uses atomic compute() operation to prevent
     * race conditions (TOCTOU - Time-of-Check to Time-of-Use).
     *
     * @param key the rate limit key
     * @param config the configuration
     * @param currentTime the current time
     * @return true if allowed, false otherwise
     */
    private boolean tryAcquireSlidingWindow(String key, RateLimitConfig config, long currentTime) {
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(
            config.getRequests(),
            config.getWindowMillis()
        );

        // Use atomic compute() to prevent race conditions (TOCTOU)
        // Always update state to track window rotation correctly, regardless of allow/deny
        AtomicBoolean allowed = new AtomicBoolean(false);
        slidingWindowCache.asMap().compute(key, (k, oldState) -> {
            SlidingWindowAlgorithm.WindowState newState = algorithm.tryConsume(oldState, currentTime);
            allowed.set(newState.isAllowed());
            return newState;  // Always persist state for correct window tracking
        });

        logger.trace("Sliding Window check for key={}, allowed={}", key, allowed.get());

        return allowed.get();
    }

    /**
     * Tries to acquire using Fixed Window algorithm.
     *
     * <p><b>Thread Safety:</b> Uses atomic compute() operation to prevent
     * race conditions (TOCTOU - Time-of-Check to Time-of-Use).
     *
     * @param key the rate limit key
     * @param config the configuration
     * @param currentTime the current time
     * @return true if allowed, false otherwise
     */
    private boolean tryAcquireFixedWindow(String key, RateLimitConfig config, long currentTime) {
        // Convert window to seconds for FixedWindowAlgorithm
        int windowSeconds = (int) (config.getWindowMillis() / 1000);
        if (windowSeconds < 1) {
            windowSeconds = 1;  // Minimum 1 second
        }
        FixedWindowAlgorithm algorithm = new FixedWindowAlgorithm(windowSeconds);

        // Use atomic compute() to prevent race conditions (TOCTOU)
        AtomicBoolean allowed = new AtomicBoolean(false);
        fixedWindowCache.asMap().compute(key, (k, oldState) -> {
            FixedWindowAlgorithm.WindowState newState = algorithm.tryAcquire(
                    oldState, config.getRequests(), currentTime);
            allowed.set(newState.isAllowed());
            return newState;
        });

        logger.trace("Fixed Window check for key={}, allowed={}", key, allowed.get());

        return allowed.get();
    }

    @Override
    public void reset(String key) {
        tokenBucketCache.invalidate(key);
        slidingWindowCache.invalidate(key);
        fixedWindowCache.invalidate(key);
        algorithmCache.invalidate(key);
        logger.debug("Reset rate limiter for key: {}", key);
    }
    
    @Override
    public Optional<RateLimitState> getState(String key) {
        RateLimitConfig.Algorithm algorithm = algorithmCache.getIfPresent(key);
        
        if (algorithm == null) {
            return Optional.empty();
        }
        
        switch (algorithm) {
            case TOKEN_BUCKET:
                TokenBucketAlgorithm.BucketState bucketState = tokenBucketCache.getIfPresent(key);
                if (bucketState != null) {
                    return Optional.of(new CaffeineRateLimitState(
                        0,  // limit unknown
                        (int) bucketState.tokens(),
                        0,  // reset time unknown
                        0   // usage unknown
                    ));
                }
                break;
                
            case SLIDING_WINDOW:
                SlidingWindowAlgorithm.WindowState windowState = slidingWindowCache.getIfPresent(key);
                if (windowState != null) {
                    int currentCount = windowState.getCurrentWindow() != null
                        ? windowState.getCurrentWindow().getCount()
                        : 0;
                    return Optional.of(new CaffeineRateLimitState(
                        0,  // limit unknown
                        0,  // remaining unknown
                        0,  // reset time unknown
                        currentCount
                    ));
                }
                break;

            case FIXED_WINDOW:
                FixedWindowAlgorithm.WindowState fixedState = fixedWindowCache.getIfPresent(key);
                if (fixedState != null) {
                    return Optional.of(new CaffeineRateLimitState(
                        0,  // limit unknown
                        0,  // remaining unknown
                        fixedState.getWindowNumber() * 1000,  // approximate reset time
                        fixedState.getRequestCount()
                    ));
                }
                break;
        }

        return Optional.empty();
    }

    @Override
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        diagnostics.put("type", "Caffeine");
        diagnostics.put("healthy", true);
        diagnostics.put("tokenBucket.size", tokenBucketCache.estimatedSize());
        diagnostics.put("tokenBucket.hitRate", getTokenBucketStats().hitRate());
        diagnostics.put("slidingWindow.size", slidingWindowCache.estimatedSize());
        diagnostics.put("slidingWindow.hitRate", getSlidingWindowStats().hitRate());
        diagnostics.put("fixedWindow.size", fixedWindowCache.estimatedSize());
        diagnostics.put("fixedWindow.hitRate", getFixedWindowStats().hitRate());

        return diagnostics;
    }

    /**
     * Gets cache statistics for Token Bucket.
     * 
     * @return cache stats
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getTokenBucketStats() {
        return tokenBucketCache.stats();
    }
    
    /**
     * Gets cache statistics for Sliding Window.
     *
     * @return cache stats
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getSlidingWindowStats() {
        return slidingWindowCache.stats();
    }

    /**
     * Gets cache statistics for Fixed Window.
     *
     * @return cache stats
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getFixedWindowStats() {
        return fixedWindowCache.stats();
    }

    /**
     * Clears all caches.
     */
    public void clearAll() {
        tokenBucketCache.invalidateAll();
        slidingWindowCache.invalidateAll();
        fixedWindowCache.invalidateAll();
        algorithmCache.invalidateAll();
        logger.info("Cleared all Caffeine caches");
    }

}
