package com.lycosoft.ratelimit.engine;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Core rate limiting engine that orchestrates all components.
 * 
 * <p>This is the main entry point for rate limit checks. It coordinates:
 * <ul>
 *   <li>Key resolution (via {@link KeyResolver})</li>
 *   <li>Storage operations (via {@link StorageProvider})</li>
 *   <li>Metrics export (via {@link MetricsExporter})</li>
 *   <li>Audit logging (via {@link AuditLogger})</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is thread-safe and can be shared across requests.
 * 
 * @since 1.0.0
 */
public class LimiterEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(LimiterEngine.class);
    
    private final StorageProvider storageProvider;
    private final KeyResolver keyResolver;
    private final MetricsExporter metricsExporter;
    private final AuditLogger auditLogger;
    
    /**
     * Creates a new limiter engine with all required components.
     * 
     * @param storageProvider the storage provider
     * @param keyResolver the key resolver
     * @param metricsExporter the metrics exporter (optional, can be no-op)
     * @param auditLogger the audit logger (optional, can be no-op)
     */
    public LimiterEngine(StorageProvider storageProvider,
                        KeyResolver keyResolver,
                        MetricsExporter metricsExporter,
                        AuditLogger auditLogger) {
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider cannot be null");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver cannot be null");
        this.metricsExporter = metricsExporter != null ? metricsExporter : new NoOpMetricsExporter();
        this.auditLogger = auditLogger != null ? auditLogger : new NoOpAuditLogger();
    }
    
    /**
     * Attempts to acquire permission for a request based on the rate limit configuration.
     * 
     * <p>This is the main entry point for rate limiting checks.
     * 
     * @param context the request context
     * @param config the rate limit configuration
     * @return the rate limit decision
     */
    public RateLimitDecision tryAcquire(RateLimitContext context, RateLimitConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Resolve the key
            String key = resolveKey(context);
            
            // 2. Get current time from storage provider (for clock sync)
            long currentTime = storageProvider.getCurrentTime();
            
            // 3. Try to acquire from storage
            boolean allowed = storageProvider.tryAcquire(key, config, currentTime);
            
            // 4. Get current state for decision metadata
            RateLimitState state = storageProvider.getState(key)
                .orElse(null);
            
            // 5. Create decision
            RateLimitDecision decision;
            if (allowed) {
                decision = RateLimitDecision.allow(
                    config.getName(),
                    config.getRequests(),
                    state != null ? state.getRemaining() : config.getRequests() - 1,
                    state != null ? state.getResetTime() : currentTime + config.getWindowMillis()
                );
                metricsExporter.recordAllow(config.getName());
            } else {
                decision = RateLimitDecision.deny(
                    config.getName(),
                    config.getRequests(),
                    state != null ? state.getResetTime() : currentTime + config.getWindowMillis(),
                    "Rate limit exceeded"
                );
                metricsExporter.recordDeny(config.getName());
                
                // Audit log enforcement
                auditLogger.logEnforcementAction(new EnforcementEventImpl(
                    config.getName(),
                    key,
                    config.getRequests(),
                    state != null ? state.getCurrentUsage() : config.getRequests(),
                    AuditLogger.EnforcementEvent.EnforcementResult.DENIED
                ));
            }
            
            // 6. Record latency
            long latency = System.currentTimeMillis() - startTime;
            metricsExporter.recordLatency(config.getName(), latency);
            
            // 7. Record usage
            if (state != null) {
                metricsExporter.recordUsage(config.getName(), state.getCurrentUsage(), config.getRequests());
            }
            
            return decision;
            
        } catch (Exception e) {
            logger.error("Error during rate limit check for limiter: {}", config.getName(), e);
            metricsExporter.recordError(config.getName(), e);
            
            // Audit log system failure
            auditLogger.logSystemFailure(new SystemFailureEventImpl(
                "LimiterEngine",
                "Rate limit check failed: " + e.getMessage(),
                e
            ));
            
            // Apply fail strategy
            return handleFailure(config, e);
        }
    }
    
    /**
     * Resolves the rate limit key from the context.
     */
    private String resolveKey(RateLimitContext context) {
        try {
            String key = keyResolver.resolveKey(context);
            if (key == null || key.trim().isEmpty()) {
                logger.warn("Key resolver returned null/empty, using global-anonymous bucket");
                return "global-anonymous";
            }
            return key;
        } catch (Exception e) {
            logger.error("Key resolution failed, using global-anonymous bucket", e);
            return "global-anonymous";
        }
    }
    
    /**
     * Handles failures based on the fail strategy.
     */
    private RateLimitDecision handleFailure(RateLimitConfig config, Exception error) {
        switch (config.getFailStrategy()) {
            case FAIL_OPEN:
                // Allow request (AP mode - availability priority)
                logger.info("Failing open for limiter: {}", config.getName());
                return RateLimitDecision.allow(
                    config.getName(),
                    config.getRequests(),
                    config.getRequests(), // Unknown remaining
                    System.currentTimeMillis() + config.getWindowMillis()
                );
                
            case FAIL_CLOSED:
                // Deny request (CP mode - consistency priority)
                logger.info("Failing closed for limiter: {}", config.getName());
                return RateLimitDecision.deny(
                    config.getName(),
                    config.getRequests(),
                    System.currentTimeMillis() + config.getWindowMillis(),
                    "Rate limiter unavailable: " + error.getMessage()
                );
                
            default:
                throw new IllegalStateException("Unknown fail strategy: " + config.getFailStrategy());
        }
    }
    
    // ========== No-Op Implementations ==========
    
    private static class NoOpAuditLogger implements AuditLogger {
        @Override
        public void logConfigChange(ConfigChangeEvent event) {}
        
        @Override
        public void logEnforcementAction(EnforcementEvent event) {}
        
        @Override
        public void logSystemFailure(SystemFailureEvent event) {}
    }
    
    // ========== Event Implementations ==========
    
    private static class EnforcementEventImpl implements AuditLogger.EnforcementEvent {
        private final String limiterName;
        private final String resolvedKey;
        private final int threshold;
        private final int currentUsage;
        private final EnforcementResult result;
        private final long timestamp;
        
        public EnforcementEventImpl(String limiterName, String resolvedKey, int threshold, 
                                   int currentUsage, EnforcementResult result) {
            this.limiterName = limiterName;
            this.resolvedKey = resolvedKey;
            this.threshold = threshold;
            this.currentUsage = currentUsage;
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String getEventType() {
            return "RATE_LIMIT_" + result.name();
        }
        
        @Override
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String getLimiterName() {
            return limiterName;
        }
        
        @Override
        public String getResolvedKey() {
            return resolvedKey;
        }
        
        @Override
        public int getThreshold() {
            return threshold;
        }
        
        @Override
        public int getCurrentUsage() {
            return currentUsage;
        }
        
        @Override
        public EnforcementResult getResult() {
            return result;
        }
    }
    
    private static class SystemFailureEventImpl implements AuditLogger.SystemFailureEvent {
        private final String component;
        private final String errorMessage;
        private final Throwable cause;
        private final long timestamp;
        
        public SystemFailureEventImpl(String component, String errorMessage, Throwable cause) {
            this.component = component;
            this.errorMessage = errorMessage;
            this.cause = cause;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String getEventType() {
            return "SYSTEM_FAILURE";
        }
        
        @Override
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String getComponent() {
            return component;
        }
        
        @Override
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public Throwable getCause() {
            return cause;
        }
    }
}
