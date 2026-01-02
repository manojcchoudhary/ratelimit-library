package com.lycosoft.ratelimit.storage;

import com.lycosoft.ratelimit.algorithm.FixedWindowAlgorithm;
import com.lycosoft.ratelimit.algorithm.TokenBucketAlgorithm;
import com.lycosoft.ratelimit.algorithm.SlidingWindowAlgorithm;
import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory implementation of {@link StorageProvider}.
 * 
 * <p>This implementation stores rate limit state in local memory using
 * ConcurrentHashMap. It's suitable for:
 * <ul>
 *   <li>Single-node deployments</li>
 *   <li>Testing and development</li>
 *   <li>L2 fallback cache</li>
 * </ul>
 * 
 * <p><b>Not suitable for:</b> Multi-node clusters (no state sharing)
 * 
 * <p><b>Clock:</b> Uses System.currentTimeMillis() (local clock)
 * 
 * @since 1.0.0
 */
public class InMemoryStorageProvider implements StorageProvider {

    private final Map<String, TokenBucketAlgorithm.BucketState> tokenBucketStates = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowAlgorithm.WindowState> slidingWindowStates = new ConcurrentHashMap<>();
    private final Map<String, FixedWindowAlgorithm.WindowState> fixedWindowStates = new ConcurrentHashMap<>();
    
    @Override
    public long getCurrentTime() {
        return System.currentTimeMillis();
    }
    
    @Override
    public boolean tryAcquire(String key, RateLimitConfig config, long currentTime) {
        return switch (config.getAlgorithm()) {
            case TOKEN_BUCKET -> tryAcquireTokenBucket(key, config, currentTime);
            case SLIDING_WINDOW -> tryAcquireSlidingWindow(key, config, currentTime);
            case FIXED_WINDOW -> tryAcquireFixedWindow(key, config, currentTime);
        };
    }
    
    private boolean tryAcquireTokenBucket(String key, RateLimitConfig config, long currentTime) {
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(
            config.getCapacity(),
            config.getRefillRate()
        );

        // Use atomic compute() to prevent race conditions (TOCTOU)
        // Always update state to track refill time correctly, regardless of allow/deny
        AtomicBoolean allowed = new AtomicBoolean(false);
        tokenBucketStates.compute(key, (k, oldState) -> {
            TokenBucketAlgorithm.BucketState newState = algorithm.tryConsume(oldState, 1, currentTime);
            allowed.set(newState.allowed());
            return newState;  // Always persist state for correct refill tracking
        });

        return allowed.get();
    }
    
    private boolean tryAcquireSlidingWindow(String key, RateLimitConfig config, long currentTime) {
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(
            config.getRequests(),
            config.getWindowMillis()
        );

        // Use atomic compute() to prevent race conditions (TOCTOU)
        // Always update state to track window rotation correctly, regardless of allow/deny
        AtomicBoolean allowed = new AtomicBoolean(false);
        slidingWindowStates.compute(key, (k, oldState) -> {
            SlidingWindowAlgorithm.WindowState newState = algorithm.tryConsume(oldState, currentTime);
            allowed.set(newState.isAllowed());
            return newState;  // Always persist state for correct window tracking
        });

        return allowed.get();
    }

    private boolean tryAcquireFixedWindow(String key, RateLimitConfig config, long currentTime) {
        // Convert window to seconds for FixedWindowAlgorithm
        int windowSeconds = (int) (config.getWindowMillis() / 1000);
        if (windowSeconds < 1) {
            windowSeconds = 1;  // Minimum 1 second
        }
        FixedWindowAlgorithm algorithm = new FixedWindowAlgorithm(windowSeconds);

        // Use atomic compute() to prevent race conditions (TOCTOU)
        AtomicBoolean allowed = new AtomicBoolean(false);
        fixedWindowStates.compute(key, (k, oldState) -> {
            FixedWindowAlgorithm.WindowState newState = algorithm.tryAcquire(
                    oldState, config.getRequests(), currentTime);
            allowed.set(newState.isAllowed());
            return newState;
        });

        return allowed.get();
    }

    @Override
    public void reset(String key) {
        tokenBucketStates.remove(key);
        slidingWindowStates.remove(key);
        fixedWindowStates.remove(key);
    }
    
    @Override
    public Optional<RateLimitState> getState(String key) {
        // Try Token Bucket first
        TokenBucketAlgorithm.BucketState bucketState = tokenBucketStates.get(key);
        if (bucketState != null) {
            return Optional.of(new SimpleRateLimitState(
                (int) bucketState.tokens(),
                (int) bucketState.tokens(),
                bucketState.lastRefillTime(),
                (int) bucketState.tokens()
            ));
        }
        
        // Try Sliding Window
        SlidingWindowAlgorithm.WindowState windowState = slidingWindowStates.get(key);
        if (windowState != null && windowState.getCurrentWindow() != null) {
            return Optional.of(new SimpleRateLimitState(
                100, // Unknown limit
                100 - windowState.getCurrentWindow().getCount(),
                windowState.getCurrentWindow().getWindowStart(),
                windowState.getCurrentWindow().getCount()
            ));
        }

        // Try Fixed Window
        FixedWindowAlgorithm.WindowState fixedState = fixedWindowStates.get(key);
        if (fixedState != null) {
            return Optional.of(new SimpleRateLimitState(
                100, // Unknown limit
                100 - fixedState.getRequestCount(),
                fixedState.getWindowNumber() * 1000, // Approximate window start
                fixedState.getRequestCount()
            ));
        }

        return Optional.empty();
    }

    @Override
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        diagnostics.put("type", "InMemory");
        diagnostics.put("healthy", true);
        diagnostics.put("states.count", this.size());

        return diagnostics;
    }

    /**
     * Clears all stored state.
     */
    public void clear() {
        tokenBucketStates.clear();
        slidingWindowStates.clear();
        fixedWindowStates.clear();
    }

    /**
     * Returns the number of keys being tracked.
     */
    public int size() {
        return tokenBucketStates.size() + slidingWindowStates.size() + fixedWindowStates.size();
    }

}
