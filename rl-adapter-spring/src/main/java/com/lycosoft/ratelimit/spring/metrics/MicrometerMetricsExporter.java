package com.lycosoft.ratelimit.spring.metrics;

import com.lycosoft.ratelimit.spi.MetricsExporter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Micrometer-based metrics exporter for Spring Boot.
 * 
 * <p>This implementation exports rate limiting metrics to Micrometer,
 * which can then be sent to various monitoring systems:
 * <ul>
 *   <li>Prometheus</li>
 *   <li>Datadog</li>
 *   <li>New Relic</li>
 *   <li>CloudWatch</li>
 *   <li>InfluxDB</li>
 * </ul>
 * 
 * <p><b>Metrics Exported:</b>
 * <ul>
 *   <li>{@code ratelimit.requests} - Counter with tags: limiter, result (allowed/denied/error)</li>
 * </ul>
 * 
 * <p><b>Example Queries (Prometheus):</b>
 * <pre>
 * # Total allowed requests
 * sum(rate(ratelimit_requests_total{result="allowed"}[5m]))
 * 
 * # Denial rate by limiter
 * rate(ratelimit_requests_total{result="denied"}[5m])
 * 
 * # Error rate
 * rate(ratelimit_requests_total{result="error"}[5m])
 * </pre>
 * 
 * @since 1.0.0
 */
public class MicrometerMetricsExporter implements MetricsExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsExporter.class);
    
    private static final String METRIC_NAME = "ratelimit.requests";
    private static final String TAG_LIMITER = "limiter";
    private static final String TAG_RESULT = "result";
    
    private final MeterRegistry registry;
    private final Map<String, Counter> counterCache;
    
    /**
     * Creates a Micrometer metrics exporter.
     * 
     * @param registry the Micrometer meter registry
     */
    public MicrometerMetricsExporter(MeterRegistry registry) {
        this.registry = registry;
        this.counterCache = new ConcurrentHashMap<>();
        logger.info("MicrometerMetricsExporter initialized");
    }
    
    @Override
    public void recordAllow(String limiterName) {
        getOrCreateCounter(limiterName, "allowed").increment();
        logger.trace("Recorded ALLOWED for limiter: {}", limiterName);
    }
    
    @Override
    public void recordDeny(String limiterName) {
        getOrCreateCounter(limiterName, "denied").increment();
        logger.trace("Recorded DENIED for limiter: {}", limiterName);
    }
    
    @Override
    public void recordError(String limiterName, Throwable error) {
        getOrCreateCounter(limiterName, "error").increment();
        logger.debug("Recorded ERROR for limiter: {}, error: {}", limiterName, error.getMessage());
    }
    
    @Override
    public void recordLatency(String limiterName, long latencyMillis) {
        io.micrometer.core.instrument.Timer.builder("ratelimit.latency")
            .tag(TAG_LIMITER, limiterName)
            .description("Rate limiter check latency")
            .register(registry)
            .record(java.time.Duration.ofMillis(latencyMillis));
        logger.debug("Recorded LATENCY for limiter: {}, latency: {}ms", limiterName, latencyMillis);
    }
    
    @Override
    public void recordFallback(String limiterName, String reason) {
        getOrCreateCounter(limiterName, "fallback").increment();
        logger.warn("Recorded FALLBACK for limiter: {}, reason: {}", limiterName, reason);
    }
    
    @Override
    public void recordCircuitBreakerStateChange(String limiterName, String newState) {
        io.micrometer.core.instrument.Gauge.builder("ratelimit.circuit.breaker", () -> stateToNumeric(newState))
            .tag(TAG_LIMITER, limiterName)
            .tag("state", newState)
            .description("Circuit breaker state (0=CLOSED, 1=HALF_OPEN, 2=OPEN)")
            .register(registry);
        logger.info("Recorded CIRCUIT_BREAKER state change for limiter: {}, state: {}", limiterName, newState);
    }
    
    @Override
    public void recordUsage(String limiterName, int current, int limit) {
        double percentage = limit > 0 ? (current * 100.0 / limit) : 0.0;
        
        io.micrometer.core.instrument.Gauge.builder("ratelimit.usage.current", () -> current)
            .tag(TAG_LIMITER, limiterName)
            .description("Current usage count")
            .register(registry);
        
        io.micrometer.core.instrument.Gauge.builder("ratelimit.usage.limit", () -> limit)
            .tag(TAG_LIMITER, limiterName)
            .description("Configured limit")
            .register(registry);
        
        io.micrometer.core.instrument.Gauge.builder("ratelimit.usage.percentage", () -> percentage)
            .tag(TAG_LIMITER, limiterName)
            .description("Usage percentage")
            .register(registry);
        
        logger.trace("Recorded USAGE for limiter: {}, current: {}, limit: {}, percentage: {}%", 
                    limiterName, current, limit, String.format("%.2f", percentage));
    }
    
    /**
     * Converts circuit breaker state to numeric value for gauges.
     */
    private double stateToNumeric(String state) {
        switch (state.toUpperCase()) {
            case "CLOSED": return 0.0;
            case "HALF_OPEN": return 1.0;
            case "OPEN": return 2.0;
            default: return -1.0;
        }
    }
    
    /**
     * Gets or creates a counter with caching.
     * 
     * <p>Counters are cached to avoid repeated registry lookups.
     * 
     * @param limiterName the limiter name
     * @param result the result type (allowed/denied/error)
     * @return the counter
     */
    private Counter getOrCreateCounter(String limiterName, String result) {
        String cacheKey = limiterName + ":" + result;
        
        return counterCache.computeIfAbsent(cacheKey, key -> {
            Tags tags = Tags.of(
                Tag.of(TAG_LIMITER, limiterName),
                Tag.of(TAG_RESULT, result)
            );
            
            Counter counter = Counter.builder(METRIC_NAME)
                .description("Rate limit request counter")
                .tags(tags)
                .register(registry);
            
            logger.debug("Created counter: {} with tags: {}", METRIC_NAME, tags);
            return counter;
        });
    }
    
    /**
     * Gets the current count for a specific limiter and result.
     * 
     * @param limiterName the limiter name
     * @param result the result type
     * @return the count
     */
    public double getCount(String limiterName, String result) {
        String cacheKey = limiterName + ":" + result;
        Counter counter = counterCache.get(cacheKey);
        return counter != null ? counter.count() : 0.0;
    }
    
    /**
     * Clears the counter cache.
     * 
     * <p>This is useful for testing. In production, counters should persist.
     */
    public void clearCache() {
        counterCache.clear();
        logger.debug("Cleared counter cache");
    }
}
