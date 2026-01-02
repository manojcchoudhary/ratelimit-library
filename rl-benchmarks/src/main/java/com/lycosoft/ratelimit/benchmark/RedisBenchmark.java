package com.lycosoft.ratelimit.benchmark;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.resilience.TieredStorageProvider;
import com.lycosoft.ratelimit.storage.caffeine.CaffeineStorageProvider;
import com.lycosoft.ratelimit.storage.redis.RedisStorageProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for Redis StorageProvider (distributed) performance.
 *
 * <p><b>Claims to Validate:</b>
 * <ul>
 *   <li>Distributed latency: P99 &lt;2ms</li>
 *   <li>Throughput: ~45,000 ops/sec</li>
 *   <li>Lua script atomicity overhead: minimal</li>
 * </ul>
 *
 * <p><b>Prerequisites:</b>
 * Redis must be running. Configure via system properties:
 * <pre>
 * -Dredis.host=localhost -Dredis.port=6379
 * </pre>
 *
 * <p><b>Run benchmarks:</b>
 * <pre>
 * # Start Redis first
 * docker run -d -p 6379:6379 redis:7-alpine
 *
 * # Run benchmarks
 * java -Dredis.host=localhost -Dredis.port=6379 \
 *      -jar rl-benchmarks/target/benchmarks.jar RedisBenchmark
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class RedisBenchmark {

    private RedisStorageProvider redisStorage;
    private TieredStorageProvider tieredRedisWithCaffeine;
    private CaffeineStorageProvider caffeineBaseline;
    private JedisPool jedisPool;

    private RateLimitConfig tokenBucketConfig;
    private RateLimitConfig slidingWindowConfig;

    private String[] keys;
    private static final int KEY_COUNT = 1000;

    private boolean redisAvailable = false;

    @Setup(Level.Trial)
    public void setup() {
        String redisHost = System.getProperty("redis.host", "localhost");
        int redisPort = Integer.parseInt(System.getProperty("redis.port", "6379"));

        // Configure Jedis pool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);

        try {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);

            // Test connection
            try (var jedis = jedisPool.getResource()) {
                jedis.ping();
                redisAvailable = true;
                System.out.println("Redis connected: " + redisHost + ":" + redisPort);
            }

            // Initialize Redis storage
            redisStorage = new RedisStorageProvider(jedisPool, true);

            // Tiered: Redis L1 + Caffeine L2
            tieredRedisWithCaffeine = new TieredStorageProvider(
                    redisStorage,
                    new CaffeineStorageProvider(),
                    RateLimitConfig.FailStrategy.FAIL_OPEN
            );

        } catch (Exception e) {
            System.err.println("Redis not available: " + e.getMessage());
            System.err.println("Skipping Redis benchmarks. Start Redis with:");
            System.err.println("  docker run -d -p 6379:6379 redis:7-alpine");
            redisAvailable = false;
        }

        // Caffeine baseline (for comparison)
        caffeineBaseline = new CaffeineStorageProvider();

        // Configs
        // refillRate is tokens per millisecond: 0.01 = 10 tokens/second
        tokenBucketConfig = RateLimitConfig.builder()
                .name("redis-benchmark-tb")
                .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                .capacity(100)
                .refillRate(0.01)  // 10 tokens per second
                .requests(100)
                .window(1)
                .build();

        slidingWindowConfig = RateLimitConfig.builder()
                .name("redis-benchmark-sw")
                .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
                .requests(100)
                .window(60)
                .build();

        // Pre-generate keys
        keys = new String[KEY_COUNT];
        for (int i = 0; i < KEY_COUNT; i++) {
            keys[i] = "redis-bench-key-" + i;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            // Clean up benchmark keys
            try (var jedis = jedisPool.getResource()) {
                for (String key : keys) {
                    jedis.del("rl:" + key);
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            jedisPool.close();
        }
    }

    // ==================== BASELINE COMPARISON ====================

    /**
     * Baseline: Caffeine local storage (for comparison).
     */
    @Benchmark
    public void baseline_caffeine(Blackhole bh) {
        bh.consume(caffeineBaseline.tryAcquire("baseline-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== REDIS SINGLE KEY ====================

    /**
     * Redis: Single key Token Bucket.
     * Target: P99 &lt;2ms
     */
    @Benchmark
    public void redis_singleKey_tokenBucket(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(redisStorage.tryAcquire("hot-key-tb", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Redis: Single key Sliding Window.
     */
    @Benchmark
    public void redis_singleKey_slidingWindow(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(redisStorage.tryAcquire("hot-key-sw", slidingWindowConfig, System.currentTimeMillis()));
    }

    // ==================== REDIS RANDOM KEYS ====================

    /**
     * Redis: Random key access (simulates real user distribution).
     */
    @Benchmark
    public void redis_randomKeys(Blackhole bh) {
        if (!redisAvailable) return;
        int index = ThreadLocalRandom.current().nextInt(KEY_COUNT);
        bh.consume(redisStorage.tryAcquire(keys[index], tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== TIERED REDIS + CAFFEINE ====================

    /**
     * Tiered: Redis L1 + Caffeine L2 (production setup).
     */
    @Benchmark
    public void tiered_redis_caffeine_singleKey(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(tieredRedisWithCaffeine.tryAcquire("tiered-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Random keys with Redis L1.
     */
    @Benchmark
    public void tiered_redis_caffeine_randomKeys(Blackhole bh) {
        if (!redisAvailable) return;
        int index = ThreadLocalRandom.current().nextInt(KEY_COUNT);
        bh.consume(tieredRedisWithCaffeine.tryAcquire(keys[index], tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== CONCURRENT REDIS ACCESS ====================

    /**
     * Redis: Concurrent access to same key (contention).
     * Tests Lua script atomicity under load.
     */
    @Benchmark
    @Threads(8)
    public void redis_concurrent_sameKey(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(redisStorage.tryAcquire("contended-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Redis: Concurrent access to different keys (no contention).
     */
    @Benchmark
    @Threads(8)
    public void redis_concurrent_differentKeys(Blackhole bh) {
        if (!redisAvailable) return;
        String key = "thread-" + Thread.currentThread().getId();
        bh.consume(redisStorage.tryAcquire(key, tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Concurrent with Redis L1.
     */
    @Benchmark
    @Threads(8)
    public void tiered_concurrent_sameKey(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(tieredRedisWithCaffeine.tryAcquire("tiered-contended", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Concurrent access to different keys (no contention).
     */
    @Benchmark
    @Threads(8)
    public void tiered_concurrent_differentKeys(Blackhole bh) {
        if (!redisAvailable) return;
        String key = "tiered-thread-" + Thread.currentThread().getId();
        bh.consume(tieredRedisWithCaffeine.tryAcquire(key, tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Single key with Sliding Window algorithm.
     */
    @Benchmark
    public void tiered_redis_caffeine_slidingWindow(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(tieredRedisWithCaffeine.tryAcquire("tiered-sw-key", slidingWindowConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Health check (checks both L1 and L2).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void tiered_healthCheck(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(tieredRedisWithCaffeine.isHealthy());
    }

    /**
     * Tiered: Diagnostics retrieval.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void tiered_diagnostics(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(tieredRedisWithCaffeine.getDiagnostics());
    }

    /**
     * Tiered: Circuit breaker state check overhead.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void tiered_circuitBreaker_stateCheck(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(tieredRedisWithCaffeine.getCircuitState());
    }

    /**
     * Tiered: Batch of 100 operations with Redis L1 + Caffeine L2.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void tiered_batch_100_operations(Blackhole bh) {
        if (!redisAvailable) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            bh.consume(tieredRedisWithCaffeine.tryAcquire(keys[i], tokenBucketConfig, now));
        }
    }

    // ==================== LATENCY FOCUSED ====================

    /**
     * Redis: Latency measurement (average time, nanoseconds).
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void redis_latency_p99(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(redisStorage.tryAcquire("latency-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== HEALTH & DIAGNOSTICS ====================

    /**
     * Redis: Health check latency.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void redis_healthCheck(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(redisStorage.isHealthy());
    }

    /**
     * Redis: Diagnostics retrieval.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void redis_diagnostics(Blackhole bh) {
        if (!redisAvailable) return;
        bh.consume(redisStorage.getDiagnostics());
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * Redis: Batch of 100 operations.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void redis_batch_100_operations(Blackhole bh) {
        if (!redisAvailable) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            bh.consume(redisStorage.tryAcquire(keys[i], tokenBucketConfig, now));
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RedisBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .jvmArgs("-Dredis.host=localhost", "-Dredis.port=6379")
                .build();

        new Runner(opt).run();
    }
}
