package com.lycosoft.ratelimit.benchmark;

import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.spring.resolver.OptimizedSpELKeyResolver;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for SpEL expression evaluation performance.
 *
 * <p><b>Claim to Validate:</b>
 * "40× performance improvement of compiled SpEL vs interpreted"
 *
 * <p><b>Benchmark Scenarios:</b>
 * <ul>
 *   <li>Static key (no SpEL): &lt;1μs baseline</li>
 *   <li>Compiled SpEL (cached): ~2μs target</li>
 *   <li>Interpreted SpEL (uncached): ~80μs (40× slower)</li>
 * </ul>
 *
 * <p><b>Run benchmarks:</b>
 * <pre>
 * java -jar rl-benchmarks/target/benchmarks.jar SpELBenchmark
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class SpELBenchmark {

    // Optimized resolver (compiled + cached)
    private OptimizedSpELKeyResolver optimizedResolver;

    // Baseline: uncached, interpreted SpEL
    private SpelExpressionParser interpretedParser;
    private String spelExpression;

    // Test contexts
    private RateLimitContext contextWithUser;
    private RateLimitContext contextWithHeaders;
    private RateLimitContext contextWithIp;
    private RateLimitContext staticKeyContext;
    private RateLimitContext complexContext;

    // Evaluation context for raw SpEL tests
    private SimpleEvaluationContext evalContext;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize optimized resolver (compiled mode)
        optimizedResolver = new OptimizedSpELKeyResolver(SpelCompilerMode.IMMEDIATE, 1000);

        // Initialize interpreted parser (no compilation)
        SpelParserConfiguration interpretedConfig = new SpelParserConfiguration(
                SpelCompilerMode.OFF, null);
        interpretedParser = new SpelExpressionParser(interpretedConfig);

        spelExpression = "#user.id + '_' + #ip";

        // Setup test contexts
        Map<String, String> headers = new HashMap<>();
        headers.put("X-API-Key", "test-api-key-12345");
        headers.put("X-Partner-ID", "partner-789");

        // Context with user principal
        contextWithUser = RateLimitContext.builder()
                .keyExpression("#user + '_' + #ip")
                .principal("user-12345")
                .remoteAddress("192.168.1.100")
                .build();

        // Context with headers
        contextWithHeaders = RateLimitContext.builder()
                .keyExpression("#headers['X-API-Key']")
                .requestHeaders(headers)
                .remoteAddress("10.0.0.1")
                .build();

        // Context with just IP
        contextWithIp = RateLimitContext.builder()
                .keyExpression("#ip")
                .remoteAddress("172.16.0.50")
                .build();

        // Static key context (no SpEL)
        staticKeyContext = RateLimitContext.builder()
                .keyExpression("static-key-no-spel")
                .remoteAddress("127.0.0.1")
                .build();

        // Complex context (multi-variable concatenation)
        complexContext = RateLimitContext.builder()
                .keyExpression("'prefix_' + #user + '_' + #ip + '_suffix'")
                .principal("complex-user")
                .remoteAddress("10.20.30.40")
                .build();

        // Setup evaluation context for raw SpEL tests
        evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        evalContext.setVariable("user", new TestUser("user-12345"));
        evalContext.setVariable("ip", "192.168.1.100");
        evalContext.setVariable("headers", headers);
    }

    // ==================== STATIC KEY BENCHMARKS ====================

    /**
     * Baseline: Static key resolution (no SpEL parsing).
     * Expected: &lt;1μs
     */
    @Benchmark
    public void staticKey_noSpEL(Blackhole bh) {
        bh.consume(optimizedResolver.resolveKey(staticKeyContext));
    }

    // ==================== COMPILED SpEL BENCHMARKS ====================

    /**
     * Compiled SpEL: Simple user + IP key.
     * Expected: ~2μs (cached + compiled bytecode)
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void compiledSpEL_userPlusIp(Blackhole bh) {
        bh.consume(optimizedResolver.resolveKey(contextWithUser));
    }

    /**
     * Compiled SpEL: Header map access.
     * Expected: ~3μs (map lookup adds slight overhead)
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void compiledSpEL_headerAccess(Blackhole bh) {
        bh.consume(optimizedResolver.resolveKey(contextWithHeaders));
    }

    /**
     * Compiled SpEL: Simple variable access.
     * Expected: ~1-2μs
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void compiledSpEL_simpleVariable(Blackhole bh) {
        bh.consume(optimizedResolver.resolveKey(contextWithIp));
    }

    // ==================== INTERPRETED SpEL BENCHMARKS (BASELINE) ====================

    /**
     * Interpreted SpEL: Parse and evaluate every time (no caching).
     * Expected: ~80μs (40× slower than compiled)
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void interpretedSpEL_parseEveryTime(Blackhole bh) {
        Expression expr = interpretedParser.parseExpression(spelExpression);
        bh.consume(expr.getValue(evalContext));
    }

    /**
     * Interpreted SpEL: Cached expression but no bytecode compilation.
     * Shows the benefit of caching even without compilation.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void interpretedSpEL_cachedExpression(Blackhole bh) {
        // Pre-parsed expression (cached)
        bh.consume(cachedInterpretedExpression.getValue(evalContext));
    }

    private Expression cachedInterpretedExpression;

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Cache the expression for the cached benchmark
        cachedInterpretedExpression = interpretedParser.parseExpression(spelExpression);
    }

    // ==================== COMPLEX EXPRESSION BENCHMARKS ====================

    /**
     * Complex SpEL: Concatenation with multiple variables.
     * Tests performance with more complex expressions.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void complexSpEL_multiVariable(Blackhole bh) {
        bh.consume(optimizedResolver.resolveKey(complexContext));
    }

    // ==================== CACHE PERFORMANCE ====================

    /**
     * Cache hit rate test: Access same expression multiple times.
     * Should be near-zero overhead after first access.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void cacheHit_sameExpression(Blackhole bh) {
        // Warm cache hit - same context used repeatedly
        bh.consume(optimizedResolver.resolveKey(contextWithUser));
    }

    /**
     * Cache miss simulation: Different expressions.
     * Note: In real scenarios, expressions are typically cached after first use.
     */
    @Benchmark
    @OperationsPerInvocation(10)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void cacheMiss_differentExpressions(Blackhole bh) {
        for (int i = 0; i < 10; i++) {
            RateLimitContext ctx = RateLimitContext.builder()
                    .keyExpression("#user + '_unique_" + i + "'")
                    .principal("user-" + i)
                    .remoteAddress("127.0.0.1")
                    .build();
            bh.consume(optimizedResolver.resolveKey(ctx));
        }
    }

    // ==================== HELPER CLASSES ====================

    public static class TestUser {
        private final String id;

        public TestUser(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SpELBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .build();

        new Runner(opt).run();
    }
}
