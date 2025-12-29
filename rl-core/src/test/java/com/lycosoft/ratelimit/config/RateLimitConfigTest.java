package com.lycosoft.ratelimit.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link RateLimitConfig}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>TTL calculation with overflow protection</li>
 *   <li>Builder validation</li>
 *   <li>Token Bucket auto-configuration</li>
 * </ul>
 */
class RateLimitConfigTest {

    // ==================== TTL Overflow Tests ====================

    @Test
    void shouldCalculateTtlNormally() {
        // Given: Normal window size
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();

        // Then: TTL should be 2 × window = 120 seconds
        assertThat(config.getTtl()).isEqualTo(120);
    }

    @Test
    void shouldCalculateTtlWithMinutes() {
        // Given: Window in minutes
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(5)
            .windowUnit(TimeUnit.MINUTES)
            .build();

        // Then: TTL should be 2 × (5 × 60) = 600 seconds
        assertThat(config.getTtl()).isEqualTo(600);
    }

    @Test
    void shouldCalculateTtlWithHours() {
        // Given: Window in hours
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(1)
            .windowUnit(TimeUnit.HOURS)
            .build();

        // Then: TTL should be 2 × 3600 = 7200 seconds
        assertThat(config.getTtl()).isEqualTo(7200);
    }

    @Test
    void shouldHandleTtlOverflowWithVeryLargeWindow() {
        // Given: Extremely large window that would cause overflow
        // Long.MAX_VALUE / 2 seconds ≈ 146 billion years
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(Long.MAX_VALUE / 2)  // Very large window
            .windowUnit(TimeUnit.SECONDS)
            .build();

        // Then: TTL should be capped at Long.MAX_VALUE / 2 (overflow protection)
        assertThat(config.getTtl()).isEqualTo(Long.MAX_VALUE / 2);
    }

    @Test
    void shouldHandleTtlOverflowWithDaysUnit() {
        // Given: Large window in days that could overflow when converted
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(Long.MAX_VALUE / (24 * 60 * 60 * 2) + 1)  // Just enough to overflow
            .windowUnit(TimeUnit.DAYS)
            .build();

        // Then: Should handle gracefully (either calculate or cap)
        assertThat(config.getTtl()).isPositive();
    }

    @Test
    void shouldHandleMaxLongWindow() {
        // Given: Maximum possible window
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(Long.MAX_VALUE)
            .windowUnit(TimeUnit.SECONDS)
            .build();

        // Then: TTL should be capped (overflow protection)
        assertThat(config.getTtl()).isEqualTo(Long.MAX_VALUE / 2);
    }

    // ==================== Builder Validation Tests ====================

    @Test
    void shouldRequireName() {
        assertThrows(NullPointerException.class, () ->
            RateLimitConfig.builder()
                .requests(100)
                .window(60)
                .build()
        );
    }

    @Test
    void shouldRequirePositiveRequests() {
        assertThrows(IllegalArgumentException.class, () ->
            RateLimitConfig.builder()
                .name("test")
                .requests(0)
                .window(60)
                .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
            RateLimitConfig.builder()
                .name("test")
                .requests(-1)
                .window(60)
                .build()
        );
    }

    @Test
    void shouldRequirePositiveWindow() {
        assertThrows(IllegalArgumentException.class, () ->
            RateLimitConfig.builder()
                .name("test")
                .requests(100)
                .window(0)
                .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
            RateLimitConfig.builder()
                .name("test")
                .requests(100)
                .window(-1)
                .build()
        );
    }

    // ==================== Token Bucket Auto-Configuration Tests ====================

    @Test
    void shouldAutoConfigureTokenBucketCapacity() {
        // Given: Token Bucket without explicit capacity
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();

        // Then: Capacity should default to requests
        assertThat(config.getCapacity()).isEqualTo(100);
    }

    @Test
    void shouldAutoConfigureTokenBucketRefillRate() {
        // Given: Token Bucket without explicit refill rate
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();

        // Then: Refill rate should be requests/windowMillis
        double expectedRate = 100.0 / 60_000.0;  // 100 requests per 60000ms
        assertThat(config.getRefillRate()).isCloseTo(expectedRate, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void shouldUseExplicitCapacityAndRefillRate() {
        // Given: Token Bucket with explicit values
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .capacity(200)
            .refillRate(5.0)
            .build();

        // Then: Explicit values should be used
        assertThat(config.getCapacity()).isEqualTo(200);
        assertThat(config.getRefillRate()).isEqualTo(5.0);
    }

    // ==================== Algorithm Tests ====================

    @Test
    void shouldDefaultToTokenBucket() {
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(60)
            .build();

        assertThat(config.getAlgorithm()).isEqualTo(RateLimitConfig.Algorithm.TOKEN_BUCKET);
    }

    @Test
    void shouldSupportSlidingWindow() {
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
            .requests(100)
            .window(60)
            .build();

        assertThat(config.getAlgorithm()).isEqualTo(RateLimitConfig.Algorithm.SLIDING_WINDOW);
    }

    // ==================== Fail Strategy Tests ====================

    @Test
    void shouldDefaultToFailOpen() {
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(60)
            .build();

        assertThat(config.getFailStrategy()).isEqualTo(RateLimitConfig.FailStrategy.FAIL_OPEN);
    }

    @Test
    void shouldSupportFailClosed() {
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(60)
            .failStrategy(RateLimitConfig.FailStrategy.FAIL_CLOSED)
            .build();

        assertThat(config.getFailStrategy()).isEqualTo(RateLimitConfig.FailStrategy.FAIL_CLOSED);
    }

    // ==================== Window Millis Tests ====================

    @Test
    void shouldConvertWindowToMillis() {
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(5)
            .windowUnit(TimeUnit.MINUTES)
            .build();

        assertThat(config.getWindowMillis()).isEqualTo(5 * 60 * 1000);
    }

    // ==================== Equals/HashCode Tests ====================

    @Test
    void shouldBeEqualWithSameValues() {
        RateLimitConfig config1 = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(60)
            .build();

        RateLimitConfig config2 = RateLimitConfig.builder()
            .name("test")
            .requests(100)
            .window(60)
            .build();

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentValues() {
        RateLimitConfig config1 = RateLimitConfig.builder()
            .name("test1")
            .requests(100)
            .window(60)
            .build();

        RateLimitConfig config2 = RateLimitConfig.builder()
            .name("test2")
            .requests(100)
            .window(60)
            .build();

        assertThat(config1).isNotEqualTo(config2);
    }

    // ==================== ToString Tests ====================

    @Test
    void shouldProduceReadableToString() {
        RateLimitConfig config = RateLimitConfig.builder()
            .name("api-limiter")
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();

        String str = config.toString();
        assertThat(str).contains("api-limiter");
        assertThat(str).contains("100");
        assertThat(str).contains("60");
    }
}
