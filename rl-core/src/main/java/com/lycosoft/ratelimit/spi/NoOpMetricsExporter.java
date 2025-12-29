package com.lycosoft.ratelimit.spi;

/**
 * No-op implementation of MetricsExporter.
 * 
 * <p>Used as a fallback when no actual metrics exporter is configured.
 * 
 * @since 1.0.0
 */
public class NoOpMetricsExporter implements MetricsExporter {
    
    @Override
    public void recordAllow(String limiterName) {
        // No-op
    }
    
    @Override
    public void recordDeny(String limiterName) {
        // No-op
    }
    
    @Override
    public void recordError(String limiterName, Throwable error) {
        // No-op
    }
    
    @Override
    public void recordFallback(String limiterName, String reason) {
        // No-op
    }
    
    @Override
    public void recordCircuitBreakerStateChange(String limiterName, String newState) {
        // No-op
    }
    
    @Override
    public void recordUsage(String limiterName, int current, int limit) {
        // No-op
    }
    
    @Override
    public void recordLatency(String limiterName, long latencyMillis) {
        // No-op
    }
}
