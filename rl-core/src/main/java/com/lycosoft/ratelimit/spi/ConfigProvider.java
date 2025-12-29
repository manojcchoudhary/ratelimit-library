package com.lycosoft.ratelimit.spi;

import com.lycosoft.ratelimit.config.RateLimitConfig;

import java.util.Optional;

/**
 * Service Provider Interface for rate limit configuration.
 * 
 * <p>Implementations provide pluggable configuration sources such as:
 * <ul>
 *   <li>Annotation metadata</li>
 *   <li>Kubernetes ConfigMaps</li>
 *   <li>Properties files</li>
 *   <li>Remote configuration services (Consul, etcd)</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> Implementations must be thread-safe.
 * 
 * @since 1.0.0
 */
public interface ConfigProvider {
    
    /**
     * Retrieves the rate limit configuration for the given identifier.
     * 
     * @param identifier the unique identifier for the rate limiter (e.g., method signature)
     * @return the configuration, or empty if not found
     */
    Optional<RateLimitConfig> getConfig(String identifier);
    
    /**
     * Registers a listener to be notified of configuration changes.
     * 
     * <p>This is used for hot-reloading support. When a configuration changes
     * (e.g., Kubernetes ConfigMap update), all registered listeners are notified.
     * 
     * @param listener the listener to register
     */
    void registerListener(ConfigChangeListener listener);
    
    /**
     * Unregisters a previously registered listener.
     * 
     * @param listener the listener to unregister
     */
    void unregisterListener(ConfigChangeListener listener);
    
    /**
     * Listener for configuration changes.
     */
    @FunctionalInterface
    interface ConfigChangeListener {
        /**
         * Called when a configuration changes.
         * 
         * @param event the change event
         */
        void onConfigChanged(ConfigChangeEvent event);
    }
    
    /**
     * Event representing a configuration change.
     */
    class ConfigChangeEvent {
        private final String identifier;
        private final RateLimitConfig oldConfig;
        private final RateLimitConfig newConfig;
        private final ChangeType changeType;
        private final String source;
        private final long timestamp;
        
        public ConfigChangeEvent(String identifier, 
                                RateLimitConfig oldConfig, 
                                RateLimitConfig newConfig,
                                ChangeType changeType,
                                String source) {
            this.identifier = identifier;
            this.oldConfig = oldConfig;
            this.newConfig = newConfig;
            this.changeType = changeType;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getIdentifier() {
            return identifier;
        }
        
        public RateLimitConfig getOldConfig() {
            return oldConfig;
        }
        
        public RateLimitConfig getNewConfig() {
            return newConfig;
        }
        
        public ChangeType getChangeType() {
            return changeType;
        }
        
        public String getSource() {
            return source;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return "ConfigChangeEvent{" +
                    "identifier='" + identifier + '\'' +
                    ", changeType=" + changeType +
                    ", source='" + source + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    
    /**
     * Type of configuration change.
     */
    enum ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }
}
