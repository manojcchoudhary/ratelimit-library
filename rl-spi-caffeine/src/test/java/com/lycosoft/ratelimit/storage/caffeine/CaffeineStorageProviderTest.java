package com.lycosoft.ratelimit.storage.caffeine;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CaffeineStorageProvider}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>Atomic compute() operations (no race conditions)</li>
 *   <li>Thread safety under high concurrency</li>
 *   <li>Token Bucket and Sliding Window algorithms</li>
 *   <li>State persistence for denied requests</li>
 * </ul>
 */
class CaffeineStorageProviderTest {

    private CaffeineStorageProvider provider;
    private RateLimitConfig tokenBucketConfig;
    private RateLimitConfig slidingWindowConfig;

    @BeforeEach
    void setUp() {
        provider = new CaffeineStorageProvider();

        tokenBucketConfig = RateLimitConfig.builder()
            .name("test-token-bucket")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(10)
            .window(1)
            .windowUnit(TimeUnit.SECONDS)
            .capacity(10)
            .refillRate(10.0 / 1000.0)  // 10 per second
            .build();

        slidingWindowConfig = RateLimitConfig.builder()
            .name("test-sliding-window")
            .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
            .requests(10)
            .window(1)
            .windowUnit(TimeUnit.SECONDS)
            .build();
    }

    // ==================== Atomic Operation Tests (Token Bucket) ====================

    @Test
    void shouldMaintainAtomicityUnderConcurrentAccessTokenBucket() throws Exception {
        // Given: Token bucket with capacity 100
        // Use a very long window (1 hour) so refill rate is negligible during test
        // (Builder auto-calculates refillRate = requests/windowMillis when refillRate=0.0)
        RateLimitConfig config = RateLimitConfig.builder()
            .name("concurrent-test")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(1)
            .windowUnit(TimeUnit.HOURS)  // 1 hour = negligible refill during test
            .capacity(100)
            .build();

        int numThreads = 20;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);

        try {
            // When: Many threads request concurrently
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < requestsPerThread; j++) {
                            long time = System.currentTimeMillis();
                            boolean allowed = provider.tryAcquire("concurrent-key", config, time);
                            if (allowed) {
                                allowedCount.incrementAndGet();
                            } else {
                                deniedCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            // Then: Exactly 100 should be allowed (capacity)
            assertThat(allowedCount.get()).isEqualTo(100);
            assertThat(deniedCount.get()).isEqualTo(numThreads * requestsPerThread - 100);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotLoseStateOnDeniedRequestTokenBucket() {
        // Given: Exhaust all tokens
        long time = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            provider.tryAcquire("key1", tokenBucketConfig, time);
        }

        // When: More requests come in (should be denied)
        boolean denied1 = provider.tryAcquire("key1", tokenBucketConfig, time);
        boolean denied2 = provider.tryAcquire("key1", tokenBucketConfig, time);

        // Then: Requests should be denied
        assertThat(denied1).isFalse();
        assertThat(denied2).isFalse();

        // And: State should still be tracked (for refill calculation)
        Optional<RateLimitState> state = provider.getState("key1");
        assertThat(state).isPresent();
    }

    @Test
    void shouldTrackRefillTimeCorrectlyTokenBucket() throws Exception {
        // Given: Exhaust tokens
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            provider.tryAcquire("key1", tokenBucketConfig, startTime);
        }

        // Request denied at startTime
        assertThat(provider.tryAcquire("key1", tokenBucketConfig, startTime)).isFalse();

        // When: Time passes (enough for 1 token to refill)
        long laterTime = startTime + 200;  // 200ms = 2 tokens refilled (10 per sec)

        // Then: Should allow request
        assertThat(provider.tryAcquire("key1", tokenBucketConfig, laterTime)).isTrue();
    }

    // ==================== Atomic Operation Tests (Sliding Window) ====================

    @Test
    void shouldMaintainAtomicityUnderConcurrentAccessSlidingWindow() throws Exception {
        // Given: Sliding window with 100 requests/second
        RateLimitConfig config = RateLimitConfig.builder()
            .name("concurrent-sw-test")
            .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
            .requests(100)
            .window(10)  // 10 second window to avoid boundary issues
            .windowUnit(TimeUnit.SECONDS)
            .build();

        int numThreads = 20;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);
        long fixedTime = System.currentTimeMillis();

        try {
            // When: Many threads request concurrently
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < requestsPerThread; j++) {
                            boolean allowed = provider.tryAcquire("sw-key", config, fixedTime);
                            if (allowed) {
                                allowedCount.incrementAndGet();
                            } else {
                                deniedCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            // Then: Exactly 100 should be allowed (limit)
            assertThat(allowedCount.get()).isEqualTo(100);
            assertThat(deniedCount.get()).isEqualTo(numThreads * requestsPerThread - 100);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotLoseStateOnDeniedRequestSlidingWindow() {
        // Given: Exhaust window
        long time = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            provider.tryAcquire("sw-key", slidingWindowConfig, time);
        }

        // When: More requests come in (should be denied)
        boolean denied1 = provider.tryAcquire("sw-key", slidingWindowConfig, time);
        boolean denied2 = provider.tryAcquire("sw-key", slidingWindowConfig, time);

        // Then: Requests should be denied
        assertThat(denied1).isFalse();
        assertThat(denied2).isFalse();

        // And: State should still be tracked
        Optional<RateLimitState> state = provider.getState("sw-key");
        assertThat(state).isPresent();
    }

    // ==================== Basic Functionality Tests ====================

    @Test
    void shouldAllowRequestsWithinLimit() {
        long time = System.currentTimeMillis();

        for (int i = 0; i < 10; i++) {
            boolean allowed = provider.tryAcquire("key1", tokenBucketConfig, time);
            assertThat(allowed).isTrue();
        }
    }

    @Test
    void shouldDenyRequestsOverLimit() {
        long time = System.currentTimeMillis();

        // Exhaust limit
        for (int i = 0; i < 10; i++) {
            provider.tryAcquire("key1", tokenBucketConfig, time);
        }

        // 11th request should be denied
        boolean allowed = provider.tryAcquire("key1", tokenBucketConfig, time);
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldTrackSeparateKeys() {
        long time = System.currentTimeMillis();

        // Exhaust key1
        for (int i = 0; i < 10; i++) {
            provider.tryAcquire("key1", tokenBucketConfig, time);
        }

        // key2 should still have capacity
        boolean allowed = provider.tryAcquire("key2", tokenBucketConfig, time);
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldResetKey() {
        long time = System.currentTimeMillis();

        // Exhaust limit
        for (int i = 0; i < 10; i++) {
            provider.tryAcquire("key1", tokenBucketConfig, time);
        }

        // Reset
        provider.reset("key1");

        // Should have full capacity again
        boolean allowed = provider.tryAcquire("key1", tokenBucketConfig, time);
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldClearAllCaches() {
        long time = System.currentTimeMillis();

        // Add state to multiple keys
        provider.tryAcquire("key1", tokenBucketConfig, time);
        provider.tryAcquire("key2", slidingWindowConfig, time);

        // Clear all
        provider.clearAll();

        // State should be empty
        assertThat(provider.getState("key1")).isEmpty();
        assertThat(provider.getState("key2")).isEmpty();
    }

    // ==================== Diagnostics Tests ====================

    @Test
    void shouldProvideDiagnostics() {
        // Given: Some activity
        long time = System.currentTimeMillis();
        provider.tryAcquire("key1", tokenBucketConfig, time);
        provider.tryAcquire("key2", slidingWindowConfig, time);

        // When: Get diagnostics
        Map<String, Object> diagnostics = provider.getDiagnostics();

        // Then: Should include relevant info
        assertThat(diagnostics).containsKey("type");
        assertThat(diagnostics).containsKey("healthy");
        assertThat(diagnostics).containsKey("tokenBucket.size");
        assertThat(diagnostics).containsKey("slidingWindow.size");
        assertThat(diagnostics.get("type")).isEqualTo("Caffeine");
        assertThat(diagnostics.get("healthy")).isEqualTo(true);
    }

    @Test
    void shouldProvideStats() {
        // Given: Some activity
        long time = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            provider.tryAcquire("key1", tokenBucketConfig, time);
        }

        // When: Get stats
        var tokenBucketStats = provider.getTokenBucketStats();
        var slidingWindowStats = provider.getSlidingWindowStats();

        // Then: Stats should be available
        assertThat(tokenBucketStats).isNotNull();
        assertThat(slidingWindowStats).isNotNull();
    }

    // ==================== Time Source Tests ====================

    @Test
    void shouldUseSystemTimeAsDefault() {
        long before = System.currentTimeMillis();
        long providerTime = provider.getCurrentTime();
        long after = System.currentTimeMillis();

        assertThat(providerTime).isBetween(before, after);
    }

    // ==================== Custom Configuration Tests ====================

    @Test
    void shouldRespectCustomConfiguration() {
        // Given: Custom configuration
        CaffeineStorageProvider customProvider = new CaffeineStorageProvider(
            100,  // max entries
            1,    // TTL
            TimeUnit.MINUTES
        );

        // When: Use provider
        long time = System.currentTimeMillis();
        boolean allowed = customProvider.tryAcquire("key1", tokenBucketConfig, time);

        // Then: Should work
        assertThat(allowed).isTrue();
    }

    // ==================== Edge Case Tests ====================

    @Test
    void shouldHandleRapidSuccessiveRequests() {
        long time = System.currentTimeMillis();

        // Rapid requests at same timestamp
        int allowedCount = 0;
        for (int i = 0; i < 20; i++) {
            if (provider.tryAcquire("rapid-key", tokenBucketConfig, time)) {
                allowedCount++;
            }
        }

        // Should allow exactly capacity
        assertThat(allowedCount).isEqualTo(10);
    }

    @Test
    void shouldHandleZeroCapacity() {
        // Note: Builder auto-calculates capacity=requests when capacity=0
        // This tests that the auto-calculation works correctly
        RateLimitConfig zeroCapacity = RateLimitConfig.builder()
            .name("zero-cap")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(1)
            .window(1)
            .windowUnit(TimeUnit.SECONDS)
            .capacity(0)  // Builder will set this to requests=1
            .refillRate(0.0)  // Builder will auto-calculate
            .build();

        // First request allowed (capacity auto-set to 1)
        boolean allowed = provider.tryAcquire("zero-key", zeroCapacity, System.currentTimeMillis());
        assertThat(allowed).isTrue();

        // Second request denied (capacity exhausted)
        boolean denied = provider.tryAcquire("zero-key", zeroCapacity, System.currentTimeMillis());
        assertThat(denied).isFalse();
    }

    @Test
    void shouldHandleLargeCapacity() {
        RateLimitConfig largeCapacity = RateLimitConfig.builder()
            .name("large-cap")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(1_000_000)
            .window(1)
            .windowUnit(TimeUnit.SECONDS)
            .capacity(1_000_000)
            .refillRate(0.0)
            .build();

        // Should allow many requests
        long time = System.currentTimeMillis();
        int allowedCount = 0;
        for (int i = 0; i < 1000; i++) {
            if (provider.tryAcquire("large-key", largeCapacity, time)) {
                allowedCount++;
            }
        }

        assertThat(allowedCount).isEqualTo(1000);
    }
}
