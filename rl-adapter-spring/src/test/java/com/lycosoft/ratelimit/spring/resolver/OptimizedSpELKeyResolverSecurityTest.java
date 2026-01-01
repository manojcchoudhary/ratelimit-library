package com.lycosoft.ratelimit.spring.resolver;

import com.lycosoft.ratelimit.engine.RateLimitContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelCompilerMode;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for OptimizedSpELKeyResolver.
 *
 * <p>Tests SpEL injection prevention (CVE mitigation) as specified in
 * Technical Specification Section 1.2: Key Resolution & SpEL Security.
 *
 * <p><b>Security Requirements:</b>
 * <ul>
 *   <li>Block Runtime.exec() injection attempts</li>
 *   <li>Block ClassLoader manipulation</li>
 *   <li>Block T() type expressions</li>
 *   <li>Block new operator</li>
 *   <li>Limit expression complexity (DoS prevention)</li>
 * </ul>
 */
class OptimizedSpELKeyResolverSecurityTest {

    private OptimizedSpELKeyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OptimizedSpELKeyResolver(SpelCompilerMode.IMMEDIATE, 100);
    }

    @Nested
    @DisplayName("SpEL Injection Prevention")
    class InjectionPrevention {

        @Test
        @DisplayName("Should block T(java.lang.Runtime).getRuntime().exec() injection")
        void shouldBlockRuntimeExecInjection() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("T(java.lang.Runtime).getRuntime().exec('ls')")
                    .build();

            assertThatThrownBy(() -> resolver.resolveKey(context))
                    .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class)
                    .hasMessageContaining("forbidden");
        }

        @Test
        @DisplayName("Should block T() type expressions")
        void shouldBlockTypeExpressions() {
            String[] maliciousExpressions = {
                    "T(java.lang.System).exit(0)",
                    "T(java.io.File).listRoots()",
                    "T(java.lang.Class).forName('Malicious')",
                    "T(java.lang.ProcessBuilder)",
            };

            for (String expr : maliciousExpressions) {
                RateLimitContext context = RateLimitContext.builder()
                        .keyExpression(expr)
                        .build();

                assertThatThrownBy(() -> resolver.resolveKey(context))
                        .as("Expression should be blocked: " + expr)
                        .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class);
            }
        }

        @Test
        @DisplayName("Should block new operator for object instantiation")
        void shouldBlockNewOperator() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("new java.io.File('/etc/passwd').exists()")
                    .build();

            assertThatThrownBy(() -> resolver.resolveKey(context))
                    .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class)
                    .hasMessageContaining("forbidden");
        }

        @Test
        @DisplayName("Should block getClass() reflection attempts")
        void shouldBlockGetClassReflection() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("#user.getClass().forName('java.lang.Runtime')")
                    .principal("testuser")
                    .build();

            assertThatThrownBy(() -> resolver.resolveKey(context))
                    .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class)
                    .hasMessageContaining("forbidden");
        }

        @Test
        @DisplayName("Should block ClassLoader access")
        void shouldBlockClassLoaderAccess() {
            String[] classLoaderExpressions = {
                    "#user.class.classLoader",
                    "T(Thread).currentThread().contextClassLoader",
            };

            for (String expr : classLoaderExpressions) {
                RateLimitContext context = RateLimitContext.builder()
                        .keyExpression(expr)
                        .principal("testuser")
                        .build();

                assertThatThrownBy(() -> resolver.resolveKey(context))
                        .as("ClassLoader expression should be blocked: " + expr)
                        .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class);
            }
        }

        @Test
        @DisplayName("Should block case-insensitive dangerous patterns")
        void shouldBlockCaseInsensitivePatterns() {
            String[] caseVariations = {
                    "t(java.lang.Runtime)",
                    "T(JAVA.LANG.RUNTIME)",
                    "NEW java.io.File('/')",
                    "GETCLASS()",
                    "FORNAME('x')",
            };

            for (String expr : caseVariations) {
                RateLimitContext context = RateLimitContext.builder()
                        .keyExpression(expr)
                        .build();

                assertThatThrownBy(() -> resolver.resolveKey(context))
                        .as("Case variation should be blocked: " + expr)
                        .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class);
            }
        }
    }

    @Nested
    @DisplayName("DoS Prevention")
    class DosPrevention {

        @Test
        @DisplayName("Should reject expressions exceeding maximum length")
        void shouldRejectLongExpressions() {
            // Create expression longer than MAX_EXPRESSION_LENGTH (500)
            StringBuilder longExpr = new StringBuilder("#user");
            for (int i = 0; i < 100; i++) {
                longExpr.append(" + '_segment").append(i).append("'");
            }

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression(longExpr.toString())
                    .principal("testuser")
                    .build();

            assertThatThrownBy(() -> resolver.resolveKey(context))
                    .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class)
                    .hasMessageContaining("too long");
        }

        @Test
        @DisplayName("Should reject deeply nested expressions")
        void shouldRejectDeeplyNestedExpressions() {
            // Create deeply nested expression (>10 levels)
            StringBuilder nestedExpr = new StringBuilder("#user");
            for (int i = 0; i < 15; i++) {
                nestedExpr.insert(0, "(");
                nestedExpr.append(")");
            }

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression(nestedExpr.toString())
                    .principal("testuser")
                    .build();

            assertThatThrownBy(() -> resolver.resolveKey(context))
                    .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class)
                    .hasMessageContaining("nested");
        }
    }

    @Nested
    @DisplayName("Safe Expression Handling")
    class SafeExpressions {

        @Test
        @DisplayName("Should allow safe variable references")
        void shouldAllowSafeVariableReferences() {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-API-Key", "test-key-12345");

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("#headers['X-API-Key']")
                    .requestHeaders(headers)
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("test-key-12345");
        }

        @Test
        @DisplayName("Should allow concatenation expressions")
        void shouldAllowConcatenation() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("#user + '_' + #ip")
                    .principal("user123")
                    .remoteAddress("192.168.1.1")
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("user123_192.168.1.1");
        }

        @Test
        @DisplayName("Should allow static keys without SpEL")
        void shouldAllowStaticKeys() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("static-rate-limit-key")
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("static-rate-limit-key");
        }

        @Test
        @DisplayName("Should handle null values gracefully")
        void shouldHandleNullValues() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("#user")
                    // principal is null
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("Should allow complex but safe header expressions")
        void shouldAllowComplexHeaderExpressions() {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Partner-ID", "partner-789");
            headers.put("X-Request-ID", "req-12345");

            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("'partner_' + #headers['X-Partner-ID']")
                    .requestHeaders(headers)
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("partner_partner-789");
        }
    }

    @Nested
    @DisplayName("Cache Security")
    class CacheSecurity {

        @Test
        @DisplayName("Should not cache malicious expressions")
        void shouldNotCacheMaliciousExpressions() {
            // First, try a malicious expression
            RateLimitContext maliciousContext = RateLimitContext.builder()
                    .keyExpression("T(java.lang.Runtime).getRuntime()")
                    .build();

            assertThatThrownBy(() -> resolver.resolveKey(maliciousContext))
                    .isInstanceOf(OptimizedSpELKeyResolver.KeyResolutionException.class);

            // Verify cache doesn't contain the malicious expression
            // (cache size should still be 0 or not include malicious expr)
            int cacheSize = resolver.getCacheSize();

            // Now try a safe expression
            RateLimitContext safeContext = RateLimitContext.builder()
                    .keyExpression("#ip")
                    .remoteAddress("127.0.0.1")
                    .build();

            String result = resolver.resolveKey(safeContext);
            assertThat(result).isEqualTo("127.0.0.1");

            // Cache should have grown by 1 (for the safe expression)
            assertThat(resolver.getCacheSize()).isEqualTo(cacheSize + 1);
        }

        @Test
        @DisplayName("Should handle cache eviction safely")
        void shouldHandleCacheEvictionSafely() {
            // Fill cache with many different expressions
            for (int i = 0; i < 150; i++) {
                RateLimitContext context = RateLimitContext.builder()
                        .keyExpression("#ip + '_" + i + "'")
                        .remoteAddress("10.0.0." + (i % 256))
                        .build();

                resolver.resolveKey(context);
            }

            // Cache should not exceed max size (100)
            assertThat(resolver.getCacheSize()).isLessThanOrEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle empty expression")
        void shouldHandleEmptyExpression() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("")
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("global");
        }

        @Test
        @DisplayName("Should handle null expression")
        void shouldHandleNullExpression() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression(null)
                    .build();

            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("global");
        }

        @Test
        @DisplayName("Should handle whitespace-only expression")
        void shouldHandleWhitespaceOnlyExpression() {
            RateLimitContext context = RateLimitContext.builder()
                    .keyExpression("   ")
                    .build();

            // Whitespace doesn't contain '#', so it's treated as static key
            String result = resolver.resolveKey(context);
            assertThat(result).isEqualTo("   ");
        }
    }
}
