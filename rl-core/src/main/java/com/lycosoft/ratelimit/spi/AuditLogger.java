package com.lycosoft.ratelimit.spi;

/**
 * Service Provider Interface for audit logging.
 * 
 * <p>Audit logging provides a tamper-evident record of all security-relevant events:
 * <ul>
 *   <li>Configuration changes (hot-reload events)</li>
 *   <li>Rate limit enforcement (allow/deny decisions)</li>
 *   <li>System failures (storage outages, security violations)</li>
 * </ul>
 * 
 * <p><b>Performance:</b> All audit logging MUST be asynchronous and non-blocking.
 * Implementations should use queues (LinkedBlockingQueue, LMAX Disruptor) to avoid
 * impacting request latency.
 * 
 * <p><b>Privacy:</b> Implementations MUST mask PII (personally identifiable information)
 * before logging. Keys should be hashed, and sensitive configuration values should be
 * masked with "********".
 * 
 * @since 1.0.0
 */
public interface AuditLogger {
    
    /**
     * Logs a configuration change event.
     * 
     * <p>Called whenever a ConfigProvider detects a configuration change
     * (e.g., Kubernetes ConfigMap reload, file update).
     * 
     * @param event the configuration change event
     */
    void logConfigChange(ConfigChangeEvent event);
    
    /**
     * Logs a rate limit enforcement action.
     * 
     * <p>Called when a request is allowed, denied, or when fallback is activated.
     * 
     * <p><b>Note:</b> In high-traffic scenarios, this may generate large volumes
     * of logs. Implementations should consider sampling strategies.
     * 
     * @param event the enforcement event
     */
    void logEnforcementAction(EnforcementEvent event);
    
    /**
     * Logs a system failure event.
     * 
     * <p>Called for critical failures such as:
     * <ul>
     *   <li>Storage provider failures</li>
     *   <li>Circuit breaker state changes</li>
     *   <li>Security violations (SpEL injection attempts)</li>
     * </ul>
     * 
     * @param event the system failure event
     */
    void logSystemFailure(SystemFailureEvent event);
    
    /**
     * Base interface for all audit events.
     */
    interface AuditEvent {
        /**
         * @return the type of event (e.g., "CONFIG_CHANGE", "RATE_LIMIT_EXCEEDED")
         */
        String getEventType();
        
        /**
         * @return the timestamp of the event (milliseconds since epoch)
         */
        long getTimestamp();
    }
    
    /**
     * Configuration change event.
     */
    interface ConfigChangeEvent extends AuditEvent {
        /**
         * @return the source of the change (e.g., file path, ConfigMap reference)
         */
        String getSource();
        
        /**
         * @return the identifier of the changed configuration
         */
        String getIdentifier();
        
        /**
         * @return the user or process that made the change (if available)
         */
        String getUserId();
    }
    
    /**
     * Rate limit enforcement event.
     */
    interface EnforcementEvent extends AuditEvent {
        /**
         * @return the name of the rate limiter
         */
        String getLimiterName();
        
        /**
         * @return the resolved key (should be hashed/masked for privacy)
         */
        String getResolvedKey();
        
        /**
         * @return the configured threshold
         */
        int getThreshold();
        
        /**
         * @return the current usage count
         */
        int getCurrentUsage();
        
        /**
         * @return the enforcement result (ALLOWED, DENIED, FALLBACK)
         */
        EnforcementResult getResult();
        
        enum EnforcementResult {
            ALLOWED,
            DENIED,
            FALLBACK
        }
    }
    
    /**
     * System failure event.
     */
    interface SystemFailureEvent extends AuditEvent {
        /**
         * @return the component that failed (e.g., "RedisStorageProvider")
         */
        String getComponent();
        
        /**
         * @return the error message
         */
        String getErrorMessage();
        
        /**
         * @return the cause of the failure (if available)
         */
        Throwable getCause();
    }
}
