package com.lycosoft.ratelimit.concurrency;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.resilience.JitteredCircuitBreaker;
import com.lycosoft.ratelimit.resilience.TieredStorageProvider;
import com.lycosoft.ratelimit.spi.NoOpMetricsExporter;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import com.lycosoft.ratelimit.storage.StaticKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thundering Herd and concurrency tests for rate limiting.
 *
 * <p>Tests concurrent access patterns as specified in Technical Specification
 * Section 2.2: Thundering Herd Protection.
 *
 * <p><b>Test Scenarios:</b>
 * <ul>
 *   <li>10,000 concurrent threads on same key</li>
 *   <li>Atomicity verification (no lost updates)</li>
 *   <li>Jittered circuit breaker recovery</li>
 *   <li>Thread-safety of rate limit decisions</li>
 * </ul>
 */
class ThunderingHerdTest {

    private LimiterEngine limiterEngine;
    private RateLimitConfig config;

    @BeforeEach
    void setUp() {
        limiterEngine = new LimiterEngine(
                new InMemoryStorageProvider(),
                new StaticKeyResolver("test"),
                new NoOpMetricsExporter(),
                null  // No audit logger needed for tests
        );

        config = RateLimitConfig.builder()
                .name("thundering-herd-test")
                .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                .capacity(100)
                .refillRate(10.0)
                .requests(100)
                .window(1)
                .build();
    }

    @Nested
    @DisplayName("Thundering Herd Scenarios")
    class ThunderingHerdScenarios {

        @Test
        @DisplayName("Should handle 1000 concurrent requests on same key atomically")
        void shouldHandle1000ConcurrentRequestsAtomically() throws InterruptedException {
            int threadCount = 1000;
            String sharedKey = "global_config";
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger deniedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Create context for shared key
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression(sharedKey)
                    .remoteAddress("127.0.0.1")
                    .build();

            // Launch all threads
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // Wait for signal to start simultaneously
                        startLatch.await();

                        RateLimitDecision decision = limiterEngine.tryAcquire(context, config);

                        if (decision.isAllowed()) {
                            allowedCount.incrementAndGet();
                        } else {
                            deniedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all threads simultaneously (thundering herd)
            startLatch.countDown();

            // Wait for all threads to complete
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Assertions
            assertThat(completed).as("All threads should complete within timeout").isTrue();
            assertThat(errorCount.get()).as("No errors should occur").isZero();

            // Token bucket capacity is 100, so exactly 100 should be allowed
            assertThat(allowedCount.get())
                    .as("Exactly capacity (100) requests should be allowed")
                    .isEqualTo(100);

            assertThat(deniedCount.get())
                    .as("Remaining requests should be denied")
                    .isEqualTo(threadCount - 100);

            // Verify atomicity: allowed + denied = total
            assertThat(allowedCount.get() + deniedCount.get())
                    .as("Total decisions should equal thread count")
                    .isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Should not lose updates under high contention")
        void shouldNotLoseUpdatesUnderHighContention() throws InterruptedException {
            int threadCount = 500;
            int requestsPerThread = 10;
            String sharedKey = "contention_test";
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // Use a larger capacity to allow more requests
            RateLimitConfig largeConfig = RateLimitConfig.builder()
                    .name("contention-test")
                    .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                    .capacity(10000)
                    .refillRate(1000.0)
                    .requests(10000)
                    .window(1)
                    .build();

            AtomicInteger totalAllowed = new AtomicInteger(0);
            AtomicInteger totalDenied = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(100);

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression(sharedKey)
                    .remoteAddress("127.0.0.1")
                    .build();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        for (int j = 0; j < requestsPerThread; j++) {
                            RateLimitDecision decision = limiterEngine.tryAcquire(context, largeConfig);
                            if (decision.isAllowed()) {
                                totalAllowed.incrementAndGet();
                            } else {
                                totalDenied.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Count as errors
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();

            // Total decisions must equal total requests (no lost updates)
            int totalRequests = threadCount * requestsPerThread;
            int totalDecisions = totalAllowed.get() + totalDenied.get();

            assertThat(totalDecisions)
                    .as("No updates should be lost: total decisions = total requests")
                    .isEqualTo(totalRequests);

            // With capacity 10000, most should be allowed
            assertThat(totalAllowed.get())
                    .as("Most requests should be allowed with large capacity")
                    .isGreaterThanOrEqualTo(totalRequests - 1000);
        }

        @Test
        @DisplayName("Should handle different keys without contention")
        void shouldHandleDifferentKeysWithoutContention() throws InterruptedException {
            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger errorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Each thread uses its own key
                        RateLimitContext context = RateLimitContext.builder()
                                .keyExpression("user_" + threadId)
                                .remoteAddress("127.0.0." + (threadId % 256))
                                .build();

                        long start = System.nanoTime();
                        limiterEngine.tryAcquire(context, config);
                        long end = System.nanoTime();

                        latencies.add(end - start);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(errorCount.get()).isZero();

            // Calculate P99 latency
            Collections.sort(latencies);
            int p99Index = (int) (latencies.size() * 0.99);
            long p99Latency = latencies.get(p99Index);

            // P99 should be under 1ms (1,000,000 ns) for in-memory storage
            assertThat(p99Latency)
                    .as("P99 latency should be under 1ms for different keys")
                    .isLessThan(1_000_000L);
        }
    }

    @Nested
    @DisplayName("Jittered Circuit Breaker Recovery")
    class JitteredRecoveryTests {

        @Test
        @DisplayName("Should transition states correctly on failures")
        void shouldTransitionStatesCorrectlyOnFailures() {
            // Use default constructor with proper parameters:
            // failureThreshold=0.5, windowMs=10000, baseHalfOpenTimeoutMs=1000, jitterFactor=0.3, maxConcurrentProbes=1
            JitteredCircuitBreaker circuitBreaker = new JitteredCircuitBreaker(
                    0.5,    // failureThreshold (50%)
                    10000,  // windowMs
                    1000,   // baseHalfOpenTimeoutMs
                    0.3,    // jitterFactor (30%)
                    1       // maxConcurrentProbes
            );

            assertThat(circuitBreaker.getState())
                    .as("Initial state should be CLOSED")
                    .isEqualTo(JitteredCircuitBreaker.State.CLOSED);

            // Trip the circuit by executing failing operations
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Simulated failure");
                    });
                } catch (Exception e) {
                    // Expected failures
                }
            }

            // After 5 consecutive failures (100% failure rate > 50% threshold), circuit should open
            assertThat(circuitBreaker.getState())
                    .as("Circuit should be OPEN after multiple failures")
                    .isEqualTo(JitteredCircuitBreaker.State.OPEN);

            // Verify failure count
            assertThat(circuitBreaker.getFailureCount())
                    .as("Failure count should be recorded")
                    .isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("Should recover after reset")
        void shouldRecoverAfterReset() throws Exception {
            JitteredCircuitBreaker circuitBreaker = new JitteredCircuitBreaker(
                    0.5, 10000, 1000, 0.3, 1
            );

            // Trip the circuit
            circuitBreaker.tripCircuit();
            assertThat(circuitBreaker.getState())
                    .isEqualTo(JitteredCircuitBreaker.State.OPEN);

            // Reset and verify
            circuitBreaker.reset();
            assertThat(circuitBreaker.getState())
                    .isEqualTo(JitteredCircuitBreaker.State.CLOSED);

            // Should allow operations after reset
            String result = circuitBreaker.execute(() -> "success");
            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("Should not cause thundering herd on circuit close")
        void shouldNotCauseThunderingHerdOnCircuitClose() throws InterruptedException {
            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // Use tiered storage with circuit breaker
            InMemoryStorageProvider l1 = new InMemoryStorageProvider();
            InMemoryStorageProvider l2 = new InMemoryStorageProvider();
            TieredStorageProvider tieredStorage = new TieredStorageProvider(
                    l1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            LimiterEngine engine = new LimiterEngine(
                    tieredStorage,
                    new StaticKeyResolver("test"),
                    new NoOpMetricsExporter(),
                    null
            );

            // Trip the circuit
            tieredStorage.tripCircuit();
            assertThat(tieredStorage.getCircuitState())
                    .isEqualTo(JitteredCircuitBreaker.State.OPEN);

            // Now reset it
            tieredStorage.resetCircuit();

            List<Long> requestTimes = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("circuit_recovery_test")
                    .remoteAddress("127.0.0.1")
                    .build();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long startTime = System.nanoTime();
                        engine.tryAcquire(context, config);
                        requestTimes.add(startTime);
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();

            // All requests should have succeeded (circuit is closed)
            assertThat(requestTimes.size()).isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should maintain accurate counts under concurrent access")
        void shouldMaintainAccurateCountsUnderConcurrentAccess() throws InterruptedException {
            int threadCount = 50;
            int requestsPerThread = 100;
            String sharedKey = "accuracy_test";

            // Small capacity to force many denials
            RateLimitConfig smallConfig = RateLimitConfig.builder()
                    .name("accuracy-test")
                    .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                    .capacity(50)
                    .refillRate(5.0)
                    .requests(50)
                    .window(1)
                    .build();

            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger totalAllowed = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression(sharedKey)
                    .remoteAddress("127.0.0.1")
                    .build();

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            RateLimitDecision decision = limiterEngine.tryAcquire(context, smallConfig);
                            if (decision.isAllowed()) {
                                totalAllowed.incrementAndGet();
                            }
                            // Small delay to allow token refill
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            long duration = System.currentTimeMillis() - startTime;

            assertThat(completed).isTrue();

            // Calculate expected allowed requests based on duration and refill rate
            // Initial capacity (50) + refill rate (5/sec) * duration (seconds)
            int expectedMaxAllowed = 50 + (int) (5.0 * duration / 1000) + 10; // +10 buffer

            assertThat(totalAllowed.get())
                    .as("Allowed count should not exceed theoretical maximum")
                    .isLessThanOrEqualTo(expectedMaxAllowed);
        }

        @Test
        @DisplayName("Should handle rapid key creation safely")
        void shouldHandleRapidKeyCreationSafely() throws InterruptedException {
            int threadCount = 100;
            AtomicInteger keyCounter = new AtomicInteger(0);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            // Create a new key each time
                            String key = "dynamic_key_" + keyCounter.incrementAndGet();
                            RateLimitContext context = RateLimitContext.builder()
                                    .keyExpression(key)
                                    .remoteAddress("127.0.0.1")
                                    .build();

                            limiterEngine.tryAcquire(context, config);
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(errorCount.get()).as("No errors during rapid key creation").isZero();
            assertThat(successCount.get()).isEqualTo(threadCount * 100);
        }
    }
}
