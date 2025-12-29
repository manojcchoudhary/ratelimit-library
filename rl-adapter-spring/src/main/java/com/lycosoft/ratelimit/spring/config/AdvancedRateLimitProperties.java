package com.lycosoft.ratelimit.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * Advanced configuration properties for rate limiting.
 * 
 * <p>This configuration class supports:
 * <ul>
 *   <li>Hop counting for X-Forwarded-For headers</li>
 *   <li>Trusted proxy CIDR ranges</li>
 *   <li>Adaptive throttling settings</li>
 *   <li>RFC 9457 Problem Details</li>
 * </ul>
 * 
 * <p><b>Example Configuration (application.yml):</b>
 * <pre>{@code
 * ratelimit:
 *   enabled: true
 *   proxy:
 *     trusted-hops: 2
 *     trusted-proxies:
 *       - 10.0.0.0/8
 *       - 172.16.0.0/12
 *       - 192.168.1.0/24
 *   throttle:
 *     enabled: true
 *     soft-limit-percentage: 80
 *     max-delay-ms: 2000
 *     strategy: LINEAR
 *   problem-details:
 *     enabled: true
 *     include-extensions: true
 * }</pre>
 * 
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "ratelimit")
public class AdvancedRateLimitProperties {
    
    /**
     * Global enable/disable for rate limiting.
     */
    private boolean enabled = true;
    
    /**
     * Proxy configuration for hop counting.
     */
    private ProxyConfig proxy = new ProxyConfig();
    
    /**
     * Adaptive throttling configuration.
     */
    private ThrottleConfig throttle = new ThrottleConfig();
    
    /**
     * RFC 9457 Problem Details configuration.
     */
    private ProblemDetailsConfig problemDetails = new ProblemDetailsConfig();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public ProxyConfig getProxy() {
        return proxy;
    }
    
    public void setProxy(ProxyConfig proxy) {
        this.proxy = proxy;
    }
    
    public ThrottleConfig getThrottle() {
        return throttle;
    }
    
    public void setThrottle(ThrottleConfig throttle) {
        this.throttle = throttle;
    }
    
    public ProblemDetailsConfig getProblemDetails() {
        return problemDetails;
    }
    
    public void setProblemDetails(ProblemDetailsConfig problemDetails) {
        this.problemDetails = problemDetails;
    }
    
    /**
     * Proxy configuration for hop counting.
     */
    public static class ProxyConfig {
        
        /**
         * Number of trusted hops to count from the right of X-Forwarded-For chain.
         * 
         * <p>Examples:
         * <ul>
         *   <li>0 = Use rightmost IP (most recent proxy)</li>
         *   <li>1 = Skip 1 from right (typical for single reverse proxy)</li>
         *   <li>2 = Skip 2 from right (typical for CDN + reverse proxy)</li>
         * </ul>
         * 
         * <p>Default: 1 (single reverse proxy)
         */
        private int trustedHops = 1;
        
        /**
         * CIDR ranges of trusted proxies.
         * 
         * <p>Only requests from these IPs will have hop counting applied.
         * Direct connections from untrusted IPs will use the connection IP.
         * 
         * <p>Examples:
         * <ul>
         *   <li>10.0.0.0/8 - Private network</li>
         *   <li>172.16.0.0/12 - Kubernetes cluster</li>
         *   <li>192.168.1.0/24 - Load balancer subnet</li>
         * </ul>
         * 
         * <p>If empty, all proxies are trusted (less secure).
         */
        private Set<String> trustedProxies = new HashSet<>();
        
        public int getTrustedHops() {
            return trustedHops;
        }
        
        public void setTrustedHops(int trustedHops) {
            this.trustedHops = trustedHops;
        }
        
        public Set<String> getTrustedProxies() {
            return trustedProxies;
        }
        
        public void setTrustedProxies(Set<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }
    }
    
    /**
     * Adaptive throttling configuration.
     */
    public static class ThrottleConfig {
        
        /**
         * Enable adaptive throttling.
         * 
         * <p>When enabled, requests between soft and hard limits will be delayed
         * instead of immediately rejected, providing graceful degradation.
         */
        private boolean enabled = false;
        
        /**
         * Soft limit as a percentage of the hard limit.
         * 
         * <p>When usage exceeds this percentage, throttling begins.
         * 
         * <p>Example: If hard limit is 100 and softLimitPercentage is 80,
         * throttling starts at 80 requests.
         * 
         * <p>Default: 80 (throttling starts at 80% of capacity)
         */
        private int softLimitPercentage = 80;
        
        /**
         * Maximum delay to inject in milliseconds.
         * 
         * <p>At the hard limit, this is the maximum delay that will be applied.
         * 
         * <p>Default: 2000ms (2 seconds)
         */
        private long maxDelayMs = 2000;
        
        /**
         * Throttle strategy: LINEAR or EXPONENTIAL.
         * 
         * <p><b>LINEAR:</b> Delay increases proportionally
         * <pre>
         * At 80%: delay = 0ms
         * At 90%: delay = 1000ms (50% of max)
         * At 100%: delay = 2000ms (100% of max)
         * </pre>
         * 
         * <p><b>EXPONENTIAL:</b> Delay increases exponentially
         * <pre>
         * At 80%: delay = 0ms
         * At 90%: delay = 500ms (25% of max)
         * At 100%: delay = 2000ms (100% of max)
         * </pre>
         * 
         * <p>Default: LINEAR
         */
        private String strategy = "LINEAR";
        
        /**
         * Use non-blocking delay (CompletableFuture) instead of Thread.sleep.
         * 
         * <p>Recommended for reactive/async applications.
         * 
         * <p>Default: false (use Thread.sleep for simplicity)
         */
        private boolean nonBlocking = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getSoftLimitPercentage() {
            return softLimitPercentage;
        }
        
        public void setSoftLimitPercentage(int softLimitPercentage) {
            if (softLimitPercentage <= 0 || softLimitPercentage >= 100) {
                throw new IllegalArgumentException("softLimitPercentage must be between 1 and 99");
            }
            this.softLimitPercentage = softLimitPercentage;
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
        
        public boolean isNonBlocking() {
            return nonBlocking;
        }
        
        public void setNonBlocking(boolean nonBlocking) {
            this.nonBlocking = nonBlocking;
        }
    }
    
    /**
     * RFC 9457 Problem Details configuration.
     */
    public static class ProblemDetailsConfig {
        
        /**
         * Enable RFC 9457 Problem Details responses.
         * 
         * <p>When enabled, 429 responses will include standardized JSON body.
         */
        private boolean enabled = true;
        
        /**
         * Include extension fields (limit, remaining, reset).
         * 
         * <p>When true, responses include detailed rate limit metadata.
         */
        private boolean includeExtensions = true;
        
        /**
         * Custom problem type URI.
         * 
         * <p>Default: https://ratelimit.io/probs/too-many-requests
         */
        private String typeUri = "https://ratelimit.io/probs/too-many-requests";
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public boolean isIncludeExtensions() {
            return includeExtensions;
        }
        
        public void setIncludeExtensions(boolean includeExtensions) {
            this.includeExtensions = includeExtensions;
        }
        
        public String getTypeUri() {
            return typeUri;
        }
        
        public void setTypeUri(String typeUri) {
            this.typeUri = typeUri;
        }
    }
}
