package com.lycosoft.ratelimit.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.spel.SpelCompilerMode;

/**
 * Configuration properties for rate limiting.
 * 
 * <p><b>Example Configuration (application.yml):</b>
 * <pre>
 * ratelimit:
 *   enabled: true
 *   spel:
 *     compiler-mode: IMMEDIATE
 *     cache-size: 1000
 * </pre>
 * 
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
    
    /**
     * Whether rate limiting is enabled.
     */
    private boolean enabled = true;
    
    /**
     * SpEL configuration.
     */
    private SpelConfig spel = new SpelConfig();
    
    /**
     * Proxy configuration for trusted proxy resolution.
     */
    private ProxyConfig proxy = new ProxyConfig();
    
    /**
     * Adaptive throttling configuration.
     */
    private ThrottlingConfig throttling = new ThrottlingConfig();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public SpelConfig getSpel() {
        return spel;
    }
    
    public void setSpel(SpelConfig spel) {
        this.spel = spel;
    }
    
    public ProxyConfig getProxy() {
        return proxy;
    }
    
    public void setProxy(ProxyConfig proxy) {
        this.proxy = proxy;
    }
    
    public ThrottlingConfig getThrottling() {
        return throttling;
    }
    
    public void setThrottling(ThrottlingConfig throttling) {
        this.throttling = throttling;
    }
    
    /**
     * SpEL compiler configuration.
     */
    public static class SpelConfig {
        
        /**
         * SpEL compiler mode.
         * 
         * <ul>
         *   <li>IMMEDIATE: Compile on first access (recommended for production)</li>
         *   <li>MIXED: Compile after several evaluations</li>
         *   <li>OFF: Never compile (useful for debugging)</li>
         * </ul>
         */
        private SpelCompilerMode compilerMode = SpelCompilerMode.IMMEDIATE;
        
        /**
         * Maximum number of compiled expressions to cache.
         */
        private int cacheSize = 1000;
        
        public SpelCompilerMode getCompilerMode() {
            return compilerMode;
        }
        
        public void setCompilerMode(SpelCompilerMode compilerMode) {
            this.compilerMode = compilerMode;
        }
        
        public int getCacheSize() {
            return cacheSize;
        }
        
        public void setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
        }
    }
    
    /**
     * Proxy configuration for trusted proxy resolution.
     */
    public static class ProxyConfig {
        
        /**
         * Number of trusted hops to count from the right in X-Forwarded-For.
         * Default: 1 (one trusted proxy).
         */
        private int trustedHops = 1;
        
        /**
         * List of trusted proxy CIDR ranges.
         * Default: localhost only (127.0.0.0/8, ::1/128).
         */
        private java.util.Set<String> trustedProxies = java.util.Set.of("127.0.0.0/8", "::1/128");
        
        public int getTrustedHops() {
            return trustedHops;
        }
        
        public void setTrustedHops(int trustedHops) {
            this.trustedHops = trustedHops;
        }
        
        public java.util.Set<String> getTrustedProxies() {
            return trustedProxies;
        }
        
        public void setTrustedProxies(java.util.Set<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }
    }
    
    /**
     * Adaptive throttling configuration.
     */
    public static class ThrottlingConfig {
        
        /**
         * Whether adaptive throttling is enabled.
         * Default: false (disabled).
         */
        private boolean enabled = false;
        
        /**
         * Soft limit threshold (percentage of hard limit).
         * Default: 80 (80% of capacity).
         */
        private int softLimit = 80;
        
        /**
         * Maximum delay to inject (milliseconds).
         * Default: 2000ms (2 seconds).
         */
        private long maxDelayMs = 2000;
        
        /**
         * Throttling strategy (LINEAR or EXPONENTIAL).
         * Default: LINEAR.
         */
        private String strategy = "LINEAR";
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getSoftLimit() {
            return softLimit;
        }
        
        public void setSoftLimit(int softLimit) {
            this.softLimit = softLimit;
        }
        
        public long getMaxDelayMs() {
            return maxDelayMs;
        }
        
        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }
        
        public String getStrategy() {
            return strategy;
        }
        
        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
    }
}
