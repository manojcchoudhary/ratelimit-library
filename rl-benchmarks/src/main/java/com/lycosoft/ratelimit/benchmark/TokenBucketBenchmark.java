package com.lycosoft.ratelimit.benchmark;

import com.lycosoft.ratelimit.algorithm.TokenBucketAlgorithm;
import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import com.lycosoft.ratelimit.storage.caffeine.CaffeineStorageProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for Token Bucket Algorithm performance.
 *
 * <p><b>Target Metrics:</b>
 * <ul>
 *   <li>P99 Latency: &lt;100μs (local Caffeine storage)</li>
 *   <li>Throughput: &gt;10M ops/sec (pure algorithm)</li>
 * </ul>
 *
 * <p><b>Run benchmarks:</b>
 * <pre>
 * mvn clean package -pl rl-benchmarks
 * java -jar rl-benchmarks/target/benchmarks.jar TokenBucketBenchmark
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class TokenBucketBenchmark {

    private TokenBucketAlgorithm algorithm;
    private TokenBucketAlgorithm.BucketState currentState;
    private LimiterEngine limiterEngine;
    private RateLimitConfig config;
    private RateLimitContext context;
    private long virtualTime;

    @Setup(Level.Trial)
    public void setup() {
        // Token Bucket: capacity=100, refill rate=10 tokens/second
        double capacity = 100.0;
        double refillRatePerMs = 10.0 / 1000.0; // 10 tokens per second
        algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);

        virtualTime = System.currentTimeMillis();
        currentState = null;

        // Setup LimiterEngine with Caffeine storage
        CaffeineStorageProvider storageProvider = new CaffeineStorageProvider();
        limiterEngine = new LimiterEngine(storageProvider);

        config = RateLimitConfig.builder()
                .name("benchmark-limiter")
                .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                .capacity(100)
                .refillRate(10.0)
                .requests(100)
                .window(1)
                .build();

        context = RateLimitContext.builder()
                .keyExpression("benchmark-key")
                .remoteAddress("127.0.0.1")
                .build();
    }

    /**
     * Benchmark: Pure Token Bucket algorithm tryConsume.
     * This measures the raw algorithm performance without storage overhead.
     */
    @Benchmark
    public void pureAlgorithm_tryConsume(Blackhole bh) {
        // Simulate time passing (1ms per request)
        virtualTime++;
        TokenBucketAlgorithm.BucketState result = algorithm.tryConsume(currentState, 1, virtualTime);
        currentState = result;
        bh.consume(result);
    }

    /**
     * Benchmark: Full LimiterEngine tryAcquire with Caffeine storage.
     * This measures end-to-end latency including storage operations.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void caffeineStorage_tryAcquire(Blackhole bh) {
        bh.consume(limiterEngine.tryAcquire(context, config));
    }

    /**
     * Benchmark: Token bucket with burst scenario.
     * Measures performance under high burst load (bucket depletion + refill).
     */
    @Benchmark
    public void burstScenario(Blackhole bh) {
        // Consume 10 tokens (simulating burst)
        for (int i = 0; i < 10; i++) {
            TokenBucketAlgorithm.BucketState result = algorithm.tryConsume(currentState, 1, virtualTime);
            currentState = result;
            bh.consume(result);
        }
        // Advance time to refill (simulate 1 second = 10 tokens)
        virtualTime += 1000;
    }

    /**
     * Benchmark: Concurrent key scenario.
     * Different keys should not contend with each other.
     */
    @Benchmark
    @Threads(4)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void concurrentKeys_tryAcquire(Blackhole bh) {
        String key = "user-" + Thread.currentThread().getId();
        RateLimitContext threadContext = RateLimitContext.builder()
                .keyExpression(key)
                .remoteAddress("127.0.0.1")
                .build();
        bh.consume(limiterEngine.tryAcquire(threadContext, config));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TokenBucketBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
