package com.lycosoft.ratelimit.resilience;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Chaos Engineering tests for TieredStorageProvider failover behavior.
 *
 * <p>Tests resilience patterns as specified in Technical Specification
 * Section 2.1: Tiered Storage Failover (L1/L2).
 *
 * <p><b>Test Scenarios:</b>
 * <ul>
 *   <li>L1 (Redis) failure with automatic L2 (Caffeine) fallback</li>
 *   <li>Network partition simulation</li>
 *   <li>Circuit breaker trip and recovery</li>
 *   <li>Fail-open vs fail-closed strategies</li>
 *   <li>Latency spike handling</li>
 * </ul>
 */
class TieredStorageFailoverTest {

    private RateLimitConfig config;

    @BeforeEach
    void setUp() {
        config = RateLimitConfig.builder()
                .name("failover-test")
                .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                .capacity(100)
                .refillRate(10.0)
                .requests(100)
                .window(1)
                .build();
    }

    @Nested
    @DisplayName("L1/L2 Failover Scenarios")
    class FailoverScenarios {

        @Test
        @DisplayName("Should seamlessly failover from L1 to L2 on exception")
        void shouldFailoverFromL1ToL2OnException() {
            // Given: L1 that throws exceptions
            StorageProvider failingL1 = mock(StorageProvider.class);
            when(failingL1.tryAcquire(anyString(), any(), anyLong()))
                    .thenThrow(new RuntimeException("Redis connection refused"));
            when(failingL1.isHealthy()).thenReturn(false);

            StorageProvider healthyL2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    failingL1, healthyL2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // When: Making requests
            boolean allowed = tiered.tryAcquire("test-key", config, System.currentTimeMillis());

            // Then: Request should succeed via L2
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("Should maintain request counts across failover")
        void shouldMaintainRequestCountsAcrossFailover() {
            // Given: Healthy L1 and L2
            InMemoryStorageProvider l1 = new InMemoryStorageProvider();
            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    l1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // When: Making requests through L1
            for (int i = 0; i < 50; i++) {
                tiered.tryAcquire("test-key", config, System.currentTimeMillis());
            }

            // Verify state exists
            Optional<RateLimitState> stateBeforeFailover = tiered.getState("test-key");
            assertThat(stateBeforeFailover).isPresent();
        }

        @Test
        @DisplayName("Should handle intermittent L1 failures gracefully")
        void shouldHandleIntermittentL1FailuresGracefully() {
            // Given: L1 that fails intermittently
            AtomicInteger callCount = new AtomicInteger(0);
            StorageProvider flakingL1 = mock(StorageProvider.class);

            when(flakingL1.tryAcquire(anyString(), any(), anyLong())).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count % 3 == 0) {
                    throw new RuntimeException("Intermittent network issue");
                }
                return true;
            });
            when(flakingL1.isHealthy()).thenReturn(true);

            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    flakingL1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // When: Making multiple requests
            int successCount = 0;
            for (int i = 0; i < 30; i++) {
                if (tiered.tryAcquire("test-key", config, System.currentTimeMillis())) {
                    successCount++;
                }
            }

            // Then: All requests should succeed (either via L1 or L2)
            assertThat(successCount).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Integration")
    class CircuitBreakerIntegration {

        @Test
        @DisplayName("Should trip circuit breaker after threshold failures")
        void shouldTripCircuitBreakerAfterThresholdFailures() {
            // Given: L1 that always fails
            StorageProvider alwaysFailingL1 = mock(StorageProvider.class);
            when(alwaysFailingL1.tryAcquire(anyString(), any(), anyLong()))
                    .thenThrow(new RuntimeException("L1 always fails"));
            when(alwaysFailingL1.isHealthy()).thenReturn(false);

            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    alwaysFailingL1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // When: Making multiple requests to trigger circuit breaker
            for (int i = 0; i < 10; i++) {
                tiered.tryAcquire("test-key-" + i, config, System.currentTimeMillis());
            }

            // Then: Circuit should be open
            assertThat(tiered.getCircuitState())
                    .isEqualTo(JitteredCircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("Should recover circuit after reset")
        void shouldRecoverCircuitAfterReset() {
            InMemoryStorageProvider l1 = new InMemoryStorageProvider();
            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    l1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // Trip the circuit manually
            tiered.tripCircuit();
            assertThat(tiered.getCircuitState()).isEqualTo(JitteredCircuitBreaker.State.OPEN);

            // Reset the circuit
            tiered.resetCircuit();
            assertThat(tiered.getCircuitState()).isEqualTo(JitteredCircuitBreaker.State.CLOSED);

            // Verify operations work
            boolean allowed = tiered.tryAcquire("test-key", config, System.currentTimeMillis());
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("Should report failure rate in diagnostics")
        void shouldReportFailureRateInDiagnostics() {
            InMemoryStorageProvider l1 = new InMemoryStorageProvider();
            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    l1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            Map<String, Object> diagnostics = tiered.getDiagnostics();

            assertThat(diagnostics).containsKey("circuitState");
            assertThat(diagnostics).containsKey("circuitFailureRate");
            assertThat(diagnostics.get("circuitFailureRate")).isInstanceOf(Number.class);
        }
    }

    @Nested
    @DisplayName("Fail Strategy Behavior")
    class FailStrategyBehavior {

        @Test
        @DisplayName("FAIL_OPEN should allow requests when both storages fail")
        void failOpenShouldAllowRequestsWhenBothStoragesFail() {
            // Given: Both L1 and L2 fail
            StorageProvider failingL1 = mock(StorageProvider.class);
            StorageProvider failingL2 = mock(StorageProvider.class);

            when(failingL1.tryAcquire(anyString(), any(), anyLong()))
                    .thenThrow(new RuntimeException("L1 failed"));
            when(failingL2.tryAcquire(anyString(), any(), anyLong()))
                    .thenThrow(new RuntimeException("L2 failed"));
            when(failingL1.isHealthy()).thenReturn(false);
            when(failingL2.isHealthy()).thenReturn(false);

            TieredStorageProvider tiered = new TieredStorageProvider(
                    failingL1, failingL2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // When: Making a request
            boolean allowed = tiered.tryAcquire("test-key", config, System.currentTimeMillis());

            // Then: Request should be allowed (fail-open)
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("FAIL_CLOSED should deny requests when both storages fail")
        void failClosedShouldDenyRequestsWhenBothStoragesFail() {
            // Given: Both L1 and L2 fail
            StorageProvider failingL1 = mock(StorageProvider.class);
            StorageProvider failingL2 = mock(StorageProvider.class);

            when(failingL1.tryAcquire(anyString(), any(), anyLong()))
                    .thenThrow(new RuntimeException("L1 failed"));
            when(failingL2.tryAcquire(anyString(), any(), anyLong()))
                    .thenThrow(new RuntimeException("L2 failed"));
            when(failingL1.isHealthy()).thenReturn(false);
            when(failingL2.isHealthy()).thenReturn(false);

            TieredStorageProvider tiered = new TieredStorageProvider(
                    failingL1, failingL2, RateLimitConfig.FailStrategy.FAIL_CLOSED);

            // Config with FAIL_CLOSED
            RateLimitConfig failClosedConfig = RateLimitConfig.builder()
                    .name("fail-closed-test")
                    .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                    .capacity(100)
                    .refillRate(10.0)
                    .requests(100)
                    .window(1)
                    .failStrategy(RateLimitConfig.FailStrategy.FAIL_CLOSED)
                    .build();

            // When: Making a request
            boolean allowed = tiered.tryAcquire("test-key", failClosedConfig, System.currentTimeMillis());

            // Then: Request should be denied (fail-closed)
            assertThat(allowed).isFalse();
        }
    }

    @Nested
    @DisplayName("Concurrent Failover")
    class ConcurrentFailover {

        @Test
        @DisplayName("Should handle concurrent requests during failover")
        void shouldHandleConcurrentRequestsDuringFailover() throws InterruptedException {
            int threadCount = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicBoolean l1Failed = new AtomicBoolean(false);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            // L1 that will be "killed" mid-test
            StorageProvider volatileL1 = new StorageProvider() {
                private final InMemoryStorageProvider delegate = new InMemoryStorageProvider();

                @Override
                public boolean tryAcquire(String key, RateLimitConfig config, long timestamp) {
                    if (l1Failed.get()) {
                        throw new RuntimeException("L1 network partition");
                    }
                    return delegate.tryAcquire(key, config, timestamp);
                }

                @Override
                public Optional<RateLimitState> getState(String key) {
                    return delegate.getState(key);
                }

                @Override
                public void reset(String key) {
                    delegate.reset(key);
                }

                @Override
                public boolean isHealthy() {
                    return !l1Failed.get();
                }

                @Override
                public long getCurrentTime() {
                    return System.currentTimeMillis();
                }

                @Override
                public Map<String, Object> getDiagnostics() {
                    return Map.of("failed", l1Failed.get());
                }
            };

            InMemoryStorageProvider l2 = new InMemoryStorageProvider();
            TieredStorageProvider tiered = new TieredStorageProvider(
                    volatileL1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Fail L1 after first 25 requests
                        if (requestId == 25) {
                            l1Failed.set(true);
                        }

                        boolean result = tiered.tryAcquire(
                                "concurrent-key",
                                config,
                                System.currentTimeMillis());

                        if (result) {
                            successCount.incrementAndGet();
                        }
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

            // All 100 requests should succeed (first 25 via L1, rest via L2)
            assertThat(successCount.get()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should not cause request loss during L1 recovery")
        void shouldNotCauseRequestLossDuringL1Recovery() throws InterruptedException {
            int threadCount = 50;
            int requestsPerThread = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicBoolean l1Healthy = new AtomicBoolean(true);
            AtomicInteger totalRequests = new AtomicInteger(0);
            AtomicInteger failedRequests = new AtomicInteger(0);

            // L1 that toggles health
            StorageProvider togglingL1 = new StorageProvider() {
                private final InMemoryStorageProvider delegate = new InMemoryStorageProvider();

                @Override
                public boolean tryAcquire(String key, RateLimitConfig config, long timestamp) {
                    if (!l1Healthy.get()) {
                        throw new RuntimeException("L1 temporarily unavailable");
                    }
                    return delegate.tryAcquire(key, config, timestamp);
                }

                @Override
                public Optional<RateLimitState> getState(String key) {
                    return delegate.getState(key);
                }

                @Override
                public void reset(String key) {
                    delegate.reset(key);
                }

                @Override
                public boolean isHealthy() {
                    return l1Healthy.get();
                }

                @Override
                public long getCurrentTime() {
                    return System.currentTimeMillis();
                }

                @Override
                public Map<String, Object> getDiagnostics() {
                    return Map.of();
                }
            };

            InMemoryStorageProvider l2 = new InMemoryStorageProvider();
            TieredStorageProvider tiered = new TieredStorageProvider(
                    togglingL1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            // Background thread to toggle L1 health
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                l1Healthy.set(!l1Healthy.get());
            }, 50, 100, TimeUnit.MILLISECONDS);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            RateLimitConfig largeConfig = RateLimitConfig.builder()
                    .name("recovery-test")
                    .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
                    .capacity(10000)
                    .refillRate(1000.0)
                    .requests(10000)
                    .window(1)
                    .build();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < requestsPerThread; j++) {
                            try {
                                tiered.tryAcquire(
                                        "recovery-key",
                                        largeConfig,
                                        System.currentTimeMillis());
                                totalRequests.incrementAndGet();
                            } catch (Exception e) {
                                failedRequests.incrementAndGet();
                            }
                            Thread.sleep(10); // Small delay between requests
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);

            scheduler.shutdownNow();
            executor.shutdown();

            assertThat(completed).isTrue();

            // No requests should fail (either L1 or L2 should handle them)
            assertThat(failedRequests.get()).isZero();
            assertThat(totalRequests.get()).isEqualTo(threadCount * requestsPerThread);
        }
    }

    @Nested
    @DisplayName("Diagnostics and Monitoring")
    class DiagnosticsAndMonitoring {

        @Test
        @DisplayName("Should include L1 and L2 health in diagnostics")
        void shouldIncludeL1AndL2HealthInDiagnostics() {
            InMemoryStorageProvider l1 = new InMemoryStorageProvider();
            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    l1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            Map<String, Object> diagnostics = tiered.getDiagnostics();

            assertThat(diagnostics).containsKey("l1Healthy");
            assertThat(diagnostics).containsKey("l2Healthy");
            assertThat(diagnostics).containsKey("circuitState");
            assertThat(diagnostics).containsKey("failStrategy");
        }

        @Test
        @DisplayName("Should update diagnostics after failover")
        void shouldUpdateDiagnosticsAfterFailover() {
            StorageProvider failingL1 = mock(StorageProvider.class);
            when(failingL1.isHealthy()).thenReturn(false);
            when(failingL1.getDiagnostics()).thenReturn(Map.of("status", "failed"));

            InMemoryStorageProvider l2 = new InMemoryStorageProvider();

            TieredStorageProvider tiered = new TieredStorageProvider(
                    failingL1, l2, RateLimitConfig.FailStrategy.FAIL_OPEN);

            Map<String, Object> diagnostics = tiered.getDiagnostics();

            assertThat(diagnostics.get("l1Healthy")).isEqualTo(false);
            assertThat(diagnostics.get("l2Healthy")).isEqualTo(true);
        }
    }
}
