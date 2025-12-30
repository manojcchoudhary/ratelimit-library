package com.lycosoft.ratelimit.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link RateLimitContext}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>Defensive copying of method arguments array</li>
 *   <li>Immutability of request headers map</li>
 *   <li>Builder validation and defaults</li>
 * </ul>
 */
class RateLimitContextTest {

    // ==================== Defensive Copy Tests (Method Arguments) ====================

    @Test
    void shouldDefensivelyCopyMethodArgumentsOnConstruction() {
        // Given: An array of arguments
        Object[] originalArgs = {"arg1", "arg2", 123};

        // When: Build context with the array
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .methodArguments(originalArgs)
            .build();

        // And: Modify the original array
        originalArgs[0] = "modified";

        // Then: Context should have the original value (defensive copy on input)
        assertThat(context.getMethodArguments()[0]).isEqualTo("arg1");
    }

    @Test
    void shouldDefensivelyCopyMethodArgumentsOnGet() {
        // Given: Context with arguments
        Object[] originalArgs = {"arg1", "arg2", 123};
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .methodArguments(originalArgs)
            .build();

        // When: Get arguments and modify the returned array
        Object[] returnedArgs = context.getMethodArguments();
        returnedArgs[0] = "modified";

        // Then: Context should still have original value (defensive copy on output)
        assertThat(context.getMethodArguments()[0]).isEqualTo("arg1");
    }

    @Test
    void shouldReturnDifferentArrayInstancesOnEachGet() {
        // Given: Context with arguments
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .methodArguments(new Object[]{"arg1"})
            .build();

        // When: Get arguments multiple times
        Object[] first = context.getMethodArguments();
        Object[] second = context.getMethodArguments();

        // Then: Should be different array instances
        assertThat(first).isNotSameAs(second);
        assertThat(first).isEqualTo(second);  // But same content
    }

    @Test
    void shouldHandleNullMethodArguments() {
        // When: Build context without method arguments
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .build();

        // Then: Should return empty array (never null)
        assertThat(context.getMethodArguments()).isNotNull();
        assertThat(context.getMethodArguments()).isEmpty();
    }

    @Test
    void shouldHandleEmptyMethodArguments() {
        // When: Build context with empty array
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .methodArguments(new Object[0])
            .build();

        // Then: Should return empty array
        assertThat(context.getMethodArguments()).isEmpty();
    }

    // ==================== Request Headers Immutability Tests ====================

    @Test
    void shouldReturnUnmodifiableHeaders() {
        // Given: Context with headers
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-Id", "abc123");

        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .requestHeaders(headers)
            .build();

        // Then: Returned map should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
            context.getRequestHeaders().put("X-New-Header", "value")
        );
    }

    @Test
    void shouldNotBeAffectedByOriginalHeaderMapModification() {
        // Given: Context with headers
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-Id", "abc123");

        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .requestHeaders(headers)
            .build();

        // When: Modify original map
        headers.put("X-New-Header", "value");
        headers.remove("X-Request-Id");

        // Then: Context headers should be unchanged
        assertThat(context.getRequestHeaders()).containsKey("X-Request-Id");
        assertThat(context.getRequestHeaders()).doesNotContainKey("X-New-Header");
    }

    @Test
    void shouldHandleNullRequestHeaders() {
        // When: Build context without headers
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .build();

        // Then: Should return empty map (never null)
        assertThat(context.getRequestHeaders()).isNotNull();
        assertThat(context.getRequestHeaders()).isEmpty();
    }

    // ==================== Builder Validation Tests ====================

    @Test
    void shouldDefaultKeyExpressionToIp() {
        RateLimitContext context = RateLimitContext.builder()
                .remoteAddress("127.0.0.1")
                .build();
        assertThat(context.getKeyExpression()).isEqualTo("#ip");
    }

    @Test
    void shouldDefaultRemoteAddressToUnknown() {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .build();

        assertThat(context.getRemoteAddress()).isEqualTo("unknown");
    }

    // ==================== Getter Tests ====================

    @Test
    void shouldReturnKeyExpression() {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user.id")
            .build();

        assertThat(context.getKeyExpression()).isEqualTo("#user.id");
    }

    @Test
    void shouldReturnPrincipal() {
        Object principal = new Object() {
            @Override
            public String toString() {
                return "TestUser";
            }
        };

        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .principal(principal)
            .build();

        assertThat(context.getPrincipal()).isSameAs(principal);
    }

    @Test
    void shouldReturnRemoteAddress() {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .remoteAddress("192.168.1.1")
            .build();

        assertThat(context.getRemoteAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldReturnMethodSignature() {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user")
            .methodSignature("com.example.Service.doSomething()")
            .build();

        assertThat(context.getMethodSignature()).isEqualTo("com.example.Service.doSomething()");
    }

    // ==================== ToString Tests ====================

    @Test
    void shouldProduceReadableToString() {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#user.id")
            .remoteAddress("192.168.1.1")
            .methodSignature("Service.method()")
            .build();

        String str = context.toString();
        assertThat(str).contains("#user.id");
        assertThat(str).contains("192.168.1.1");
        assertThat(str).contains("Service.method()");
    }

    // ==================== Complex Object Tests ====================

    @Test
    void shouldHandleComplexMethodArguments() {
        // Given: Complex arguments including nested objects
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("key", "value");

        Object[] args = {
            "string",
            123,
            new String[]{"a", "b"},
            nestedMap
        };

        // When: Build context
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("#args[0]")
            .methodArguments(args)
            .build();

        // Then: Arguments should be accessible
        Object[] retrieved = context.getMethodArguments();
        assertThat(retrieved).hasSize(4);
        assertThat(retrieved[0]).isEqualTo("string");
        assertThat(retrieved[1]).isEqualTo(123);
        assertThat(retrieved[2]).isEqualTo(new String[]{"a", "b"});
        assertThat(retrieved[3]).isEqualTo(nestedMap);
    }
}
