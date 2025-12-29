package com.lycosoft.ratelimit.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for RFC 7231 compliant rate limit response headers.
 * 
 * <p><b>Standard Headers:</b>
 * <ul>
 *   <li><b>Retry-After:</b> Seconds until retry allowed</li>
 *   <li><b>RateLimit-Limit:</b> Maximum requests allowed</li>
 *   <li><b>RateLimit-Remaining:</b> Requests remaining in window</li>
 *   <li><b>RateLimit-Reset:</b> Unix timestamp when limit resets</li>
 *   <li><b>RateLimit-Policy:</b> Policy description (e.g., "100;w=60")</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b>
 * <pre>
 * Map&lt;String, String&gt; headers = RateLimitHeaders.builder()
 *     .retryAfter(24)
 *     .limit(100)
 *     .remaining(0)
 *     .reset(System.currentTimeMillis() / 1000 + 60)
 *     .policy("100;w=60")
 *     .build();
 * </pre>
 * 
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7231.html#section-7.1.3">RFC 7231</a>
 * @since 1.0.0
 */
public class RateLimitHeaders {
    
    public static final String RETRY_AFTER = "Retry-After";
    public static final String RATELIMIT_LIMIT = "RateLimit-Limit";
    public static final String RATELIMIT_REMAINING = "RateLimit-Remaining";
    public static final String RATELIMIT_RESET = "RateLimit-Reset";
    public static final String RATELIMIT_POLICY = "RateLimit-Policy";
    
    private final Map<String, String> headers;
    
    private RateLimitHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Map<String, String> toMap() {
        return new LinkedHashMap<>(headers);
    }
    
    public static class Builder {
        private final Map<String, String> headers = new LinkedHashMap<>();
        
        public Builder retryAfter(long seconds) {
            headers.put(RETRY_AFTER, String.valueOf(seconds));
            return this;
        }
        
        public Builder limit(long limit) {
            headers.put(RATELIMIT_LIMIT, String.valueOf(limit));
            return this;
        }
        
        public Builder remaining(long remaining) {
            headers.put(RATELIMIT_REMAINING, String.valueOf(remaining));
            return this;
        }
        
        public Builder reset(long resetTimestamp) {
            headers.put(RATELIMIT_RESET, String.valueOf(resetTimestamp));
            return this;
        }
        
        public Builder policy(String policy) {
            headers.put(RATELIMIT_POLICY, policy);
            return this;
        }
        
        public Builder policy(long limit, long windowSeconds) {
            return policy(limit + ";w=" + windowSeconds);
        }
        
        public RateLimitHeaders build() {
            return new RateLimitHeaders(headers);
        }
    }
}
