package com.lycosoft.ratelimit.benchmark;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import com.lycosoft.ratelimit.storage.caffeine.CaffeineStorageProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for StorageProvider implementations.
 *
 * <p><b>Claims to Validate:</b>
 * <ul>
 *   <li>Local (Caffeine): P99 &lt;100μs, Throughput &gt;8M ops/sec</li>
 *   <li>InMemory: P99 &lt;50μs (baseline)</li>
 *   <li>O(1) memory complexity (no GC pressure growth)</li>
 * </ul>
 *
 * <p><b>Run benchmarks:</b>
 * <pre>
 * java -jar rl-benchmarks/target/benchmarks.jar StorageBenchmark
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G", "-XX:+UseG1GC"})
public class StorageBenchmark {

    @Param({"caffeine", "inmemory"})
    private String storageType;

    private StorageProvider storageProvider;
    private RateLimitConfig tokenBucketConfig;
    private RateLimitConfig slidingWindowConfig;

    // Pre-generated keys for consistent benchmarking
    private String[] keys;
    private static final int KEY_COUNT = 10000;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize storage provider based on parameter
        switch (storageType) {
            case "caffeine":
                storageProvider = new CaffeineStorageProvider();
                break;
            case "inmemory":
            default:
                storageProvider = new InMemoryStorageProvider();
                break;
        }

        // Token Bucket config
        tokenBucketConfig = RateLimitConfig.builder()
                .name("storage-benchmark-tb")
                .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                .capacity(100)
                .refillRate(10.0)
                .requests(100)
                .window(1)
                .build();

        // Sliding Window config
        slidingWindowConfig = RateLimitConfig.builder()
                .name("storage-benchmark-sw")
                .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
                .requests(100)
                .window(60)
                .build();

        // Pre-generate keys
        keys = new String[KEY_COUNT];
        for (int i = 0; i < KEY_COUNT; i++) {
            keys[i] = "user-" + i + "-key";
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Cleanup
        if (storageProvider != null) {
            for (String key : keys) {
                storageProvider.reset(key);
            }
        }
    }

    // ==================== SINGLE KEY BENCHMARKS ====================

    /**
     * Single key tryAcquire (Token Bucket).
     * Tests hot-path performance with cache hits.
     */
    @Benchmark
    public void singleKey_tryAcquire_tokenBucket(Blackhole bh) {
        bh.consume(storageProvider.tryAcquire("hot-key-tb", tokenBucketConfig,
                System.currentTimeMillis()));
    }

    /**
     * Single key tryAcquire (Sliding Window).
     * Sliding window has slightly more state to manage.
     */
    @Benchmark
    public void singleKey_tryAcquire_slidingWindow(Blackhole bh) {
        bh.consume(storageProvider.tryAcquire("hot-key-sw", slidingWindowConfig,
                System.currentTimeMillis()));
    }

    /**
     * Single key getState.
     * Read-only operation, should be very fast.
     */
    @Benchmark
    public void singleKey_getState(Blackhole bh) {
        Optional<RateLimitState> state = storageProvider.getState("hot-key-tb");
        bh.consume(state);
    }

    // ==================== RANDOM KEY BENCHMARKS ====================

    /**
     * Random key access (simulates real-world user distribution).
     * Tests cache efficiency with many different keys.
     */
    @Benchmark
    public void randomKey_tryAcquire(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(KEY_COUNT);
        bh.consume(storageProvider.tryAcquire(keys[index], tokenBucketConfig,
                System.currentTimeMillis()));
    }

    /**
     * Random key getState.
     * Tests read performance across many keys.
     */
    @Benchmark
    public void randomKey_getState(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(KEY_COUNT);
        bh.consume(storageProvider.getState(keys[index]));
    }

    // ==================== CONCURRENT ACCESS BENCHMARKS ====================

    /**
     * Concurrent access to same key (contention scenario).
     * Tests thread-safety overhead.
     */
    @Benchmark
    @Threads(8)
    public void concurrent_sameKey(Blackhole bh) {
        bh.consume(storageProvider.tryAcquire("contended-key", tokenBucketConfig,
                System.currentTimeMillis()));
    }

    /**
     * Concurrent access to different keys (no contention).
     * Should scale linearly with threads.
     */
    @Benchmark
    @Threads(8)
    public void concurrent_differentKeys(Blackhole bh) {
        String key = "thread-" + Thread.currentThread().getId() + "-key";
        bh.consume(storageProvider.tryAcquire(key, tokenBucketConfig,
                System.currentTimeMillis()));
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * Batch of 100 tryAcquire operations.
     * Simulates high-throughput scenario.
     */
    @Benchmark
    @OperationsPerInvocation(100)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void batch_100_tryAcquire(Blackhole bh) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            bh.consume(storageProvider.tryAcquire(keys[i % KEY_COUNT], tokenBucketConfig, now));
        }
    }

    // ==================== RESET OPERATIONS ====================

    /**
     * Reset operation benchmark.
     * Tests cleanup performance.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void reset_singleKey(Blackhole bh) {
        // First create state, then reset
        storageProvider.tryAcquire("reset-key", tokenBucketConfig, System.currentTimeMillis());
        storageProvider.reset("reset-key");
        bh.consume(true);
    }

    // ==================== HEALTH CHECK ====================

    /**
     * Health check benchmark.
     * Should be very fast (no I/O).
     */
    @Benchmark
    public void healthCheck(Blackhole bh) {
        bh.consume(storageProvider.isHealthy());
    }

    /**
     * Diagnostics retrieval.
     * Tests monitoring overhead.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void getDiagnostics(Blackhole bh) {
        bh.consume(storageProvider.getDiagnostics());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(StorageBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
