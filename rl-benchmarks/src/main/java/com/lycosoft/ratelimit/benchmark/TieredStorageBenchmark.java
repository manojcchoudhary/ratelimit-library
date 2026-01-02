package com.lycosoft.ratelimit.benchmark;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.resilience.TieredStorageProvider;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import com.lycosoft.ratelimit.storage.caffeine.CaffeineStorageProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for TieredStorageProvider (L1/L2) performance.
 *
 * <p><b>Claims to Validate:</b>
 * <ul>
 *   <li>L1 (primary) latency: ~same as underlying storage</li>
 *   <li>L2 fallback latency: minimal overhead on failover</li>
 *   <li>Circuit breaker overhead: negligible when closed</li>
 * </ul>
 *
 * <p><b>Run benchmarks:</b>
 * <pre>
 * java -jar rl-benchmarks/target/benchmarks.jar TieredStorageBenchmark
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class TieredStorageBenchmark {

    // Storage providers
    private CaffeineStorageProvider caffeineOnly;
    private InMemoryStorageProvider inMemoryOnly;
    private TieredStorageProvider tieredCaffeineCaffeine;
    private TieredStorageProvider tieredInMemoryInMemory;
    private TieredStorageProvider tieredWithFailingL1;

    private RateLimitConfig tokenBucketConfig;
    private RateLimitConfig slidingWindowConfig;
    private String[] keys;
    private static final int KEY_COUNT = 1000;

    @Setup(Level.Trial)
    public void setup() {
        // Single-tier providers (for comparison)
        caffeineOnly = new CaffeineStorageProvider();
        inMemoryOnly = new InMemoryStorageProvider();

        // Tiered: Caffeine L1 + Caffeine L2
        tieredCaffeineCaffeine = new TieredStorageProvider(
                new CaffeineStorageProvider(),
                new CaffeineStorageProvider(),
                RateLimitConfig.FailStrategy.FAIL_OPEN
        );

        // Tiered: InMemory L1 + InMemory L2
        tieredInMemoryInMemory = new TieredStorageProvider(
                new InMemoryStorageProvider(),
                new InMemoryStorageProvider(),
                RateLimitConfig.FailStrategy.FAIL_OPEN
        );

        // Tiered with simulated L1 failure (always uses L2)
        StorageProvider failingL1 = new FailingStorageProvider();
        tieredWithFailingL1 = new TieredStorageProvider(
                failingL1,
                new CaffeineStorageProvider(),
                RateLimitConfig.FailStrategy.FAIL_OPEN
        );

        // Token Bucket config
        // refillRate is tokens per millisecond: 0.01 = 10 tokens/second
        tokenBucketConfig = RateLimitConfig.builder()
                .name("tiered-benchmark-tb")
                .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                .capacity(100)
                .refillRate(0.01)  // 10 tokens per second
                .requests(100)
                .window(1)
                .build();

        // Sliding Window config
        slidingWindowConfig = RateLimitConfig.builder()
                .name("tiered-benchmark-sw")
                .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
                .requests(100)
                .window(60)
                .build();

        // Pre-generate keys
        keys = new String[KEY_COUNT];
        for (int i = 0; i < KEY_COUNT; i++) {
            keys[i] = "tiered-key-" + i;
        }
    }

    // ==================== BASELINE BENCHMARKS ====================

    /**
     * Baseline: Caffeine only (no tiering overhead).
     */
    @Benchmark
    public void baseline_caffeineOnly(Blackhole bh) {
        bh.consume(caffeineOnly.tryAcquire("baseline-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Baseline: InMemory only (fastest possible).
     */
    @Benchmark
    public void baseline_inMemoryOnly(Blackhole bh) {
        bh.consume(inMemoryOnly.tryAcquire("baseline-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== TIERED STORAGE BENCHMARKS ====================

    /**
     * Tiered: Caffeine L1 + Caffeine L2 (normal operation, Token Bucket).
     * Measures overhead of tiering abstraction.
     */
    @Benchmark
    public void tiered_caffeine_caffeine_tokenBucket(Blackhole bh) {
        bh.consume(tieredCaffeineCaffeine.tryAcquire("hot-key-tb", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Caffeine L1 + Caffeine L2 (Sliding Window).
     */
    @Benchmark
    public void tiered_caffeine_caffeine_slidingWindow(Blackhole bh) {
        bh.consume(tieredCaffeineCaffeine.tryAcquire("hot-key-sw", slidingWindowConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: InMemory L1 + InMemory L2 (fastest tiered, Token Bucket).
     */
    @Benchmark
    public void tiered_inmemory_inmemory_tokenBucket(Blackhole bh) {
        bh.consume(tieredInMemoryInMemory.tryAcquire("hot-key-tb", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: InMemory L1 + InMemory L2 (Sliding Window).
     */
    @Benchmark
    public void tiered_inmemory_inmemory_slidingWindow(Blackhole bh) {
        bh.consume(tieredInMemoryInMemory.tryAcquire("hot-key-sw", slidingWindowConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Random key access pattern.
     */
    @Benchmark
    public void tiered_randomKeys(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(KEY_COUNT);
        bh.consume(tieredCaffeineCaffeine.tryAcquire(keys[index], tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== FAILOVER BENCHMARKS ====================

    /**
     * Tiered: L1 always fails, measures L2 fallback latency.
     * This simulates Redis being down.
     */
    @Benchmark
    public void tiered_l1Failure_l2Fallback(Blackhole bh) {
        bh.consume(tieredWithFailingL1.tryAcquire("failover-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== CONCURRENT ACCESS ====================

    /**
     * Tiered: Concurrent access to same key.
     */
    @Benchmark
    @Threads(8)
    public void tiered_concurrent_sameKey(Blackhole bh) {
        bh.consume(tieredCaffeineCaffeine.tryAcquire("contended-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Tiered: Concurrent access to different keys.
     */
    @Benchmark
    @Threads(8)
    public void tiered_concurrent_differentKeys(Blackhole bh) {
        String key = "thread-" + Thread.currentThread().getId();
        bh.consume(tieredCaffeineCaffeine.tryAcquire(key, tokenBucketConfig, System.currentTimeMillis()));
    }

    // ==================== CIRCUIT BREAKER OVERHEAD ====================

    /**
     * Measures circuit breaker check overhead (circuit closed).
     */
    @Benchmark
    public void circuitBreaker_closedState(Blackhole bh) {
        // Circuit is closed, should have minimal overhead
        bh.consume(tieredCaffeineCaffeine.getCircuitState());
        bh.consume(tieredCaffeineCaffeine.tryAcquire("cb-key", tokenBucketConfig, System.currentTimeMillis()));
    }

    /**
     * Measures diagnostics retrieval overhead.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void diagnostics_retrieval(Blackhole bh) {
        bh.consume(tieredCaffeineCaffeine.getDiagnostics());
    }

    /**
     * Measures health check overhead.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void healthCheck_overhead(Blackhole bh) {
        bh.consume(tieredCaffeineCaffeine.isHealthy());
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Storage provider that always fails (simulates Redis down).
     */
    static class FailingStorageProvider implements StorageProvider {
        @Override
        public boolean tryAcquire(String key, RateLimitConfig config, long timestamp) {
            throw new RuntimeException("Simulated L1 failure");
        }

        @Override
        public java.util.Optional<com.lycosoft.ratelimit.spi.RateLimitState> getState(String key) {
            throw new RuntimeException("Simulated L1 failure");
        }

        @Override
        public void reset(String key) {
            throw new RuntimeException("Simulated L1 failure");
        }

        @Override
        public boolean isHealthy() {
            return false;
        }

        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }

        @Override
        public java.util.Map<String, Object> getDiagnostics() {
            return java.util.Map.of("status", "failed", "type", "simulated-failure");
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TieredStorageBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
