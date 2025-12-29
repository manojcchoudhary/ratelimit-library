package com.lycosoft.ratelimit.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RateLimitProblemDetail}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>JSON escaping of special characters (RFC 8259)</li>
 *   <li>RFC 9457 Problem Detail structure</li>
 *   <li>Extension fields</li>
 * </ul>
 */
class RateLimitProblemDetailTest {

    // ==================== JSON Escaping Tests ====================

    @Test
    void shouldEscapeBackslash() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Path: C:\\Users\\test")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("C:\\\\Users\\\\test");
    }

    @Test
    void shouldEscapeDoubleQuote() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("He said \"hello\"")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("He said \\\"hello\\\"");
    }

    @Test
    void shouldEscapeNewline() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Line 1\nLine 2")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("Line 1\\nLine 2");
    }

    @Test
    void shouldEscapeCarriageReturn() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Line 1\rLine 2")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("Line 1\\rLine 2");
    }

    @Test
    void shouldEscapeTab() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Col1\tCol2")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("Col1\\tCol2");
    }

    @Test
    void shouldEscapeBackspace() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Hello\bWorld")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("Hello\\bWorld");
    }

    @Test
    void shouldEscapeFormFeed() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Page1\fPage2")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("Page1\\fPage2");
    }

    @Test
    void shouldEscapeControlCharacters() {
        // Test various control characters (U+0000 to U+001F)
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Start\u0001\u0002\u0003End")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("\\u0001");
        assertThat(json).contains("\\u0002");
        assertThat(json).contains("\\u0003");
    }

    @Test
    void shouldEscapeNullCharacter() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Before\u0000After")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("\\u0000");
    }

    @Test
    void shouldHandleMultipleEscapesInSameString() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Path: \"C:\\temp\\\"\nNew line\there")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("\\\"");
        assertThat(json).contains("\\\\");
        assertThat(json).contains("\\n");
        assertThat(json).contains("\\t");
    }

    @Test
    void shouldHandleNullDetailGracefully() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail(null)
            .build();

        String json = detail.toJson();
        // Should not contain "detail" field or should handle null properly
        assertThat(json).isNotNull();
    }

    @Test
    void shouldPreserveNormalCharacters() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("Normal ASCII text 123 !@# αβγ 中文")
            .build();

        String json = detail.toJson();
        assertThat(json).contains("Normal ASCII text 123 !@#");
    }

    // ==================== RFC 9457 Structure Tests ====================

    @Test
    void shouldIncludeRequiredFields() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .type("https://example.com/problem")
            .title("Problem Title")
            .status(429)
            .build();

        Map<String, Object> map = detail.toMap();

        assertThat(map).containsEntry("type", "https://example.com/problem");
        assertThat(map).containsEntry("title", "Problem Title");
        assertThat(map).containsEntry("status", 429);
    }

    @Test
    void shouldUseDefaultsForRateLimiting() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder().build();

        assertThat(detail.getType()).isEqualTo(RateLimitProblemDetail.TYPE_TOO_MANY_REQUESTS);
        assertThat(detail.getTitle()).isEqualTo(RateLimitProblemDetail.TITLE_TOO_MANY_REQUESTS);
        assertThat(detail.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldIncludeDetailAndInstance() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .detail("You have exceeded your quota")
            .instance("/api/v1/orders")
            .build();

        assertThat(detail.getDetail()).isEqualTo("You have exceeded your quota");
        assertThat(detail.getInstance()).isEqualTo("/api/v1/orders");
    }

    // ==================== Extension Fields Tests ====================

    @Test
    void shouldSupportExtensionFields() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .extension("retry_after", 30)
            .extension("limit", 100)
            .extension("remaining", 0)
            .build();

        assertThat(detail.getExtension("retry_after")).isEqualTo(30);
        assertThat(detail.getExtension("limit")).isEqualTo(100);
        assertThat(detail.getExtension("remaining")).isEqualTo(0);
    }

    @Test
    void shouldIncludeExtensionsInMap() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .extension("custom_field", "custom_value")
            .build();

        Map<String, Object> map = detail.toMap();
        assertThat(map).containsEntry("custom_field", "custom_value");
    }

    @Test
    void shouldSupportRateLimitFieldsShortcut() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .rateLimitFields(100, 0, 30, 1640995200)
            .build();

        assertThat(detail.getExtension("limit")).isEqualTo(100);
        assertThat(detail.getExtension("remaining")).isEqualTo(0);
        assertThat(detail.getExtension("retry_after")).isEqualTo(30L);
        assertThat(detail.getExtension("reset")).isEqualTo(1640995200L);
    }

    @Test
    void shouldGetRetryAfterAsLong() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .extension("retry_after", 45)
            .build();

        assertThat(detail.getRetryAfter()).isEqualTo(45L);
    }

    @Test
    void shouldReturnNullForMissingRetryAfter() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder().build();

        assertThat(detail.getRetryAfter()).isNull();
    }

    // ==================== Factory Method Tests ====================

    @Test
    void shouldCreateTooManyRequestsDetail() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.tooManyRequests(
            "/api/v1/orders", 30
        );

        assertThat(detail.getType()).isEqualTo(RateLimitProblemDetail.TYPE_TOO_MANY_REQUESTS);
        assertThat(detail.getTitle()).isEqualTo("Too Many Requests");
        assertThat(detail.getStatus()).isEqualTo(429);
        assertThat(detail.getInstance()).isEqualTo("/api/v1/orders");
        assertThat(detail.getRetryAfter()).isEqualTo(30L);
        assertThat(detail.getDetail()).contains("30 seconds");
    }

    @Test
    void shouldCreateForRateLimitExceeded() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.forRateLimitExceeded(
            "api-orders", "/api/v1/orders", 45
        );

        assertThat(detail.getType()).isEqualTo(RateLimitProblemDetail.TYPE_TOO_MANY_REQUESTS);
        assertThat(detail.getExtension("limiter")).isEqualTo("api-orders");
        assertThat(detail.getRetryAfter()).isEqualTo(45L);
        assertThat(detail.getDetail()).contains("api-orders");
    }

    // ==================== JSON Output Tests ====================

    @Test
    void shouldProduceValidJson() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .type("https://example.com/problem")
            .title("Test")
            .status(429)
            .detail("Test detail")
            .instance("/test")
            .extension("retry_after", 30)
            .build();

        String json = detail.toJson();

        // Basic JSON structure validation
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"type\":");
        assertThat(json).contains("\"title\":");
        assertThat(json).contains("\"status\":");
        assertThat(json).contains("\"detail\":");
        assertThat(json).contains("\"instance\":");
        assertThat(json).contains("\"retry_after\":");
    }

    @Test
    void shouldHandleNumericExtensions() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .extension("int_value", 42)
            .extension("long_value", 9999999999L)
            .extension("double_value", 3.14)
            .build();

        String json = detail.toJson();

        // Numbers should not be quoted
        assertThat(json).contains(":42");
        assertThat(json).contains(":9999999999");
        assertThat(json).contains(":3.14");
    }

    @Test
    void shouldHandleNullExtension() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .extension("null_value", null)
            .build();

        String json = detail.toJson();
        assertThat(json).contains("\"null_value\":null");
    }

    // ==================== Immutability Tests ====================

    @Test
    void shouldReturnCopyOfExtensions() {
        RateLimitProblemDetail detail = RateLimitProblemDetail.builder()
            .extension("key", "value")
            .build();

        Map<String, Object> extensions = detail.getExtensions();

        // Modifying returned map should not affect internal state
        extensions.put("new_key", "new_value");

        assertThat(detail.getExtension("new_key")).isNull();
    }
}
