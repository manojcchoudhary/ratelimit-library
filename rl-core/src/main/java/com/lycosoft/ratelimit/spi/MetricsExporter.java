package com.lycosoft.ratelimit.spi;

/**
 * Service Provider Interface for exporting rate limiter metrics.
 * 
 * <p>Implementations integrate with observability platforms such as:
 * <ul>
 *   <li>Micrometer (Spring Boot)</li>
 *   <li>SmallRye Metrics (Quarkus)</li>
 *   <li>Prometheus directly</li>
 *   <li>StatsD</li>
 *   <li>Datadog</li>
 * </ul>
 * 
 * <p><b>Performance:</b> Metric recording must be non-blocking and should not
 * impact request latency. Use async counters where possible.
 * 
 * @since 1.0.0
 */
public interface MetricsExporter {
    
    /**
     * Records a successful rate limit check (request allowed).
     * 
     * @param limiterName the name of the rate limiter
     */
    void recordAllow(String limiterName);
    
    /**
     * Records a rate limit denial (request blocked).
     * 
     * @param limiterName the name of the rate limiter
     */
    void recordDeny(String limiterName);
    
    /**
     * Records a rate limiter error (e.g., storage failure).
     * 
     * @param limiterName the name of the rate limiter
     * @param error the error that occurred
     */
    void recordError(String limiterName, Throwable error);
    
    /**
     * Records L1 to L2 fallback activation.
     * 
     * @param limiterName the name of the rate limiter
     * @param reason the reason for fallback (e.g., "REDIS_TIMEOUT")
     */
    void recordFallback(String limiterName, String reason);
    
    /**
     * Records circuit breaker state change.
     * 
     * @param limiterName the name of the rate limiter
     * @param newState the new circuit breaker state (OPEN, HALF_OPEN, CLOSED)
     */
    void recordCircuitBreakerStateChange(String limiterName, String newState);
    
    /**
     * Records the current usage of a rate limiter.
     * 
     * <p>This is useful for capacity planning and alerting.
     * 
     * @param limiterName the name of the rate limiter
     * @param current the current usage count
     * @param limit the configured limit
     */
    void recordUsage(String limiterName, int current, int limit);
    
    /**
     * Records the latency of a rate limit check.
     * 
     * @param limiterName the name of the rate limiter
     * @param latencyMillis the latency in milliseconds
     */
    void recordLatency(String limiterName, long latencyMillis);
}
