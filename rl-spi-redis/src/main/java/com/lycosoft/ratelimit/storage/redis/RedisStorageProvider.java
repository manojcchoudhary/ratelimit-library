package com.lycosoft.ratelimit.storage.redis;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.SecureStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis-based storage provider for rate limiting.
 *
 * <p>This implementation uses Lua scripts to ensure atomicity of rate limit operations.
 * All scripts are versioned and automatically reloaded on version mismatch.
 *
 * <p><b>Clock Synchronization:</b> Uses {@code REDIS.TIME()} command to ensure
 * cluster-wide time consistency, preventing window fragmentation due to clock skew.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Atomic operations via Lua scripts</li>
 *   <li>Version-controlled Lua scripts</li>
 *   <li>Connection pooling</li>
 *   <li>TTL-based cleanup</li>
 *   <li>Distributed clock sync</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class RedisStorageProvider implements StorageProvider {

    private static final Logger logger = LoggerFactory.getLogger(RedisStorageProvider.class);

    private final JedisPool jedisPool;
    private final VersionedLuaScriptManager scriptManager;

    // Script names
    private static final String TOKEN_BUCKET_SCRIPT = LuaScripts.TOKEN_BUCKET;
    private static final String SLIDING_WINDOW_SCRIPT = LuaScripts.SLIDING_WINDOW;

    private final boolean useRedisTime;

    private static final ThreadLocal<String[]> KEYS_BUFFER =
            ThreadLocal.withInitial(() -> new String[1]);

    private static final ThreadLocal<String[]> TOKEN_BUCKET_ARGS_BUFFER =
            ThreadLocal.withInitial(() -> new String[5]);

    private static final ThreadLocal<String[]> SLIDING_WINDOW_ARGS_BUFFER =
            ThreadLocal.withInitial(() -> new String[4]);  // 4 elements: limit, window_size, current_time, ttl

    // Atomic cache entry to prevent non-atomic reads of cachedTime and cacheExpiry
    private static final long CACHE_TTL_MS = 100; // Cache for 100ms

    private record CacheEntry(long cachedTime, long cacheExpiry) {}
    private final AtomicReference<CacheEntry> timeCache = new AtomicReference<>(new CacheEntry(0, 0));

    /**
     * Creates a Redis storage provider with the given Jedis pool.
     *
     * @param jedisPool the Jedis connection pool
     */
    public RedisStorageProvider(JedisPool jedisPool, boolean useRedisTime) {
        Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        VersionedLuaScriptManager tempScriptManager = new VersionedLuaScriptManager();

        // Pre-load scripts on initialization
        try (Jedis jedis = jedisPool.getResource()) {
            tempScriptManager.loadScript(jedis, TOKEN_BUCKET_SCRIPT);
            tempScriptManager.loadScript(jedis, SLIDING_WINDOW_SCRIPT);
            logger.info("RedisStorageProvider initialized with {} scripts loaded", 2);
        } catch (Exception e) {
            logger.error("Failed to pre-load Lua scripts", e);
            throw new RuntimeException("Redis initialization failed", e);
        }

        this.jedisPool = jedisPool;
        this.scriptManager = tempScriptManager;
        this.useRedisTime = useRedisTime;
    }

    @Override
    public long getCurrentTime() {
        long now = System.currentTimeMillis();

        // Check local cache first to prevent Redis DoS vector.
        // Use atomic read to prevent inconsistent state between cachedTime and cacheExpiry
        CacheEntry cache = timeCache.get();
        if (now < cache.cacheExpiry()) {
            return cache.cachedTime() + (now - (cache.cacheExpiry() - CACHE_TTL_MS));
        }

        if (!useRedisTime) {
            return now; // No network call
        }

        try (Jedis jedis = jedisPool.getResource()) {
            // Use REDIS.TIME() for cluster-wide clock synchronization
            // Returns: [seconds, microseconds]
            List<String> time = jedis.time();
            long seconds = Long.parseLong(time.get(0));
            long microseconds = Long.parseLong(time.get(1));

            // Convert to milliseconds
            long redisTime = (seconds * 1000) + (microseconds / 1000);

            // Atomic update of cache
            timeCache.set(new CacheEntry(redisTime, now + CACHE_TTL_MS));

            return redisTime;
        } catch (Exception e) {
            logger.warn("Failed to get Redis time, falling back to System time", e);
            return System.currentTimeMillis();
        }
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig config, long currentTime) {
        long startNanos = System.nanoTime();
        // Validate inputs
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Rate limit key cannot be null or empty");
        }

        if (config == null) {
            throw new IllegalArgumentException("RateLimitConfig cannot be null");
        }

        if (currentTime <= 0) {
            logger.warn("Invalid currentTime: {}, using System.currentTimeMillis()", currentTime);
            currentTime = System.currentTimeMillis();
        }

        if (config.getAlgorithm() == null) {
            throw new IllegalArgumentException("Algorithm cannot be null in config");
        }

        // Validate algorithm-specific parameters
        switch (config.getAlgorithm()) {
            case TOKEN_BUCKET:
                if (config.getCapacity() <= 0) {
                    throw new IllegalArgumentException("Token bucket capacity must be positive");
                }
                if (config.getRefillRate() <= 0) {
                    throw new IllegalArgumentException("Token bucket refill rate must be positive");
                }
                break;

            case SLIDING_WINDOW:
                if (config.getRequests() <= 0) {
                    throw new IllegalArgumentException("Sliding window requests must be positive");
                }
                if (config.getWindowMillis() <= 0) {
                    throw new IllegalArgumentException("Sliding window duration must be positive");
                }
                break;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = switch (config.getAlgorithm()) {
                case TOKEN_BUCKET -> executeTokenBucketScript(jedis, key, config, currentTime);
                case SLIDING_WINDOW -> executeSlidingWindowScript(jedis, key, config, currentTime);
                default -> throw new IllegalArgumentException("Unsupported algorithm: " + config.getAlgorithm());
            };

            // Calculate duration AFTER the actual Redis operation
            long durationMicros = (System.nanoTime() - startNanos) / 1000;

            // Validate and parse result safely
            if (!(result instanceof List)) {
                logger.error("Unexpected Lua script return type: {}, key: {}",
                        result.getClass().getName(), maskKey(key));
                throw new SecureStorageException("Service temporarily unavailable", "Invalid Lua script response type");
            }

            @SuppressWarnings("unchecked")
            List<Long> scriptResult = (List<Long>) result;

            if (scriptResult.isEmpty()) {
                logger.error("Lua script returned empty list for key: {}", maskKey(key));
                throw new SecureStorageException("Service temporarily unavailable", "Malformed Lua script response");
            }

            // Safe access with bounds checking
            Long allowedValue = scriptResult.get(0);
            if (allowedValue == null) {
                logger.error("Lua script returned null allowed value for key: {}", maskKey(key));
                throw new SecureStorageException("Service temporarily unavailable", "Null value in Lua script response");
            }

            boolean allowed = allowedValue == 1;

            // Warn on slow operations (use masked key for PII protection)
            if (durationMicros > 5000) { // >5ms
                logger.warn("Slow rate limit check: key={}, duration={}μs, algorithm={}",
                        maskKey(key), durationMicros, config.getAlgorithm());
            }

            // Info-level sampling for production visibility (use masked key for PII protection)
            if (ThreadLocalRandom.current().nextInt(1000) == 0) { // 0.1% sampling
                logger.info("Rate limit: key={}, allowed={}, duration={}μs, algo={}",
                        maskKey(key), allowed, durationMicros, config.getAlgorithm());
            }

            logger.debug("Rate limit check: key={}, allowed={}, remaining={}, duration={}μs",
                    maskKey(key), allowed, scriptResult.get(1), durationMicros);

            return allowed;

        } catch (Exception e) {
            long durationMicros = (System.nanoTime() - startNanos) / 1000;
            logger.error("Rate limit error: key={}, duration={}μs, error={}",
                    maskKey(key), durationMicros, e.getMessage(), e);
            throw new SecureStorageException("Service temporarily unavailable", "Failed to check rate limit", e);
        }
    }

    /**
     * Masks a key for logging to protect PII.
     * Shows first 4 and last 4 characters, masks the middle.
     *
     * @param key the key to mask
     * @return the masked key
     */
    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    /**
     * Executes the Token Bucket Lua script.
     *
     * @param jedis       the Redis connection
     * @param key         the rate limit key
     * @param config      the rate limit configuration
     * @param currentTime the current time in milliseconds
     * @return the script result: [allowed, remaining_tokens]
     */
    private Object executeTokenBucketScript(Jedis jedis, String key,
                                            RateLimitConfig config, long currentTime) {

        String[] keys = KEYS_BUFFER.get();
        keys[0] = key;

        String[] args = TOKEN_BUCKET_ARGS_BUFFER.get();
        args[0] = String.valueOf(config.getCapacity());
        args[1] = String.valueOf(config.getRefillRate());
        args[2] = "1";  // tokens_required (constant)
        args[3] = String.valueOf(currentTime);
        args[4] = String.valueOf(config.getTtl());

        return scriptManager.evalsha(jedis, TOKEN_BUCKET_SCRIPT, keys, args);
    }

    /**
     * Executes the Sliding Window Lua script.
     *
     * @param jedis       the Redis connection
     * @param key         the rate limit key
     * @param config      the rate limit configuration
     * @param currentTime the current time in milliseconds
     * @return the script result: [allowed, remaining]
     */
    private Object executeSlidingWindowScript(Jedis jedis, String key,
                                              RateLimitConfig config, long currentTime) {

        String[] keys = KEYS_BUFFER.get();
        keys[0] = key;

        String[] args = SLIDING_WINDOW_ARGS_BUFFER.get();
        args[0] = String.valueOf(config.getRequests()); // limit
        args[1] = String.valueOf(config.getWindowMillis()); // window_size
        args[2] = String.valueOf(currentTime); // current_time (fixed: was incorrectly at index 3)
        args[3] = String.valueOf(config.getTtl()); // ttl (fixed: was incorrectly at index 4)

        return scriptManager.evalsha(jedis, SLIDING_WINDOW_SCRIPT, keys, args);
    }

    @Override
    public void reset(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        try (Jedis jedis = jedisPool.getResource()) {
            Long deleted = jedis.del(key);
            logger.debug("Reset rate limiter for key: {} (deleted: {})", key, deleted);
        } catch (JedisConnectionException e) {
            logger.error("Redis connection failed while resetting key: {}", key, e);
            throw new SecureStorageException("Service temporarily unavailable", "Redis connection unavailable", e);
        } catch (JedisDataException e) {
            logger.error("Redis data error while resetting key: {}", key, e);
            throw new SecureStorageException("Service temporarily unavailable", "Invalid Redis operation", e);
        } catch (Exception e) {
            logger.error("Unexpected error resetting key: {}", key, e);
            throw new SecureStorageException("Service temporarily unavailable", "Failed to reset rate limiter", e);
        }
    }

    @Override
    public Optional<RateLimitState> getState(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Check if key exists
            if (!jedis.exists(key)) {
                return Optional.empty();
            }

            // For Token Bucket, get tokens and last_refill
            List<String> state = jedis.hmget(key, "tokens", "last_refill");

            if (state.get(0) == null) {
                // Not a token bucket key, might be sliding window
                return Optional.empty();
            }

            double tokens = Double.parseDouble(state.get(0));

            // Return a basic state (limited information available)
            return Optional.of(new RedisRateLimitState(
                    0,  // limit unknown from current state
                    (int) tokens,  // remaining tokens
                    0,  // reset time unknown
                    0   // current usage unknown
            ));

        } catch (Exception e) {
            logger.warn("Failed to get state for key: " + key, e);
            return Optional.empty();
        }
    }

    /**
     * Closes the Jedis pool.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("RedisStorageProvider closed");
        }
    }

    /**
     * Gets the script manager (for testing).
     *
     * @return the script manager
     */
    VersionedLuaScriptManager getScriptManager() {
        return scriptManager;
    }

    /**
     * Performs a health check on the Redis connection.
     *
     * @return true if Redis is healthy, false otherwise
     */
    @Override
    public boolean isHealthy() {
        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.ping();
            return "PONG".equals(response);
        } catch (Exception e) {
            logger.error("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets diagnostic information about the storage provider.
     *
     * @return map of diagnostic keys and values
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        try {
            diagnostics.put("type", "Redis");
            diagnostics.put("healthy", isHealthy());
            diagnostics.put("pool.active", jedisPool.getNumActive());
            diagnostics.put("pool.idle", jedisPool.getNumIdle());
            diagnostics.put("pool.waiting", jedisPool.getNumWaiters());
            diagnostics.put("pool.max", jedisPool.getMaxTotal());
            diagnostics.put("scripts.loaded", scriptManager.getCachedScriptCount());
        } catch (Exception e) {
            diagnostics.put("error", e.getMessage());
            diagnostics.put("healthy", false);
        }
        return diagnostics;
    }
}
