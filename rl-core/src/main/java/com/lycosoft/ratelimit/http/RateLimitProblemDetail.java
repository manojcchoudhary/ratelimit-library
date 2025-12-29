package com.lycosoft.ratelimit.http;

import java.util.HashMap;
import java.util.Map;

/**
 * RFC 9457 Problem Details for HTTP APIs - Rate Limiting.
 * 
 * <p>This class provides standardized machine-readable error responses
 * for rate limit exceeded scenarios.
 * 
 * <p><b>RFC 9457 Format:</b>
 * <pre>{@code
 * {
 *   "type": "https://ratelimit.io/probs/too-many-requests",
 *   "title": "Too Many Requests",
 *   "status": 429,
 *   "detail": "Quota exceeded for the current window. Please try again in 24 seconds.",
 *   "instance": "/api/v1/orders",
 *   "retry_after": 24,
 *   "limit": 100,
 *   "remaining": 0,
 *   "reset": 1640995200
 * }
 * }</pre>
 * 
 * <p><b>Standard Fields (RFC 9457):</b>
 * <ul>
 *   <li><b>type:</b> URI identifying the problem type</li>
 *   <li><b>title:</b> Short human-readable summary</li>
 *   <li><b>status:</b> HTTP status code (429)</li>
 *   <li><b>detail:</b> Human-readable explanation specific to this occurrence</li>
 *   <li><b>instance:</b> URI reference identifying this specific occurrence</li>
 * </ul>
 * 
 * <p><b>Extension Fields (Rate Limiting):</b>
 * <ul>
 *   <li><b>retry_after:</b> Seconds until retry is allowed</li>
 *   <li><b>limit:</b> The rate limit threshold</li>
 *   <li><b>remaining:</b> Remaining quota</li>
 *   <li><b>reset:</b> Unix timestamp when quota resets</li>
 * </ul>
 * 
 * @since 1.1.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457.html">RFC 9457</a>
 */
public class RateLimitProblemDetail {
    
    /**
     * Standard problem type URI for rate limiting.
     */
    public static final String TYPE_TOO_MANY_REQUESTS = "https://ratelimit.io/probs/too-many-requests";
    
    /**
     * Standard title for rate limit errors.
     */
    public static final String TITLE_TOO_MANY_REQUESTS = "Too Many Requests";
    
    /**
     * HTTP 429 status code.
     */
    public static final int STATUS_TOO_MANY_REQUESTS = 429;
    
    private final String type;
    private final String title;
    private final int status;
    private final String detail;
    private final String instance;
    private final Map<String, Object> extensions;
    
    private RateLimitProblemDetail(Builder builder) {
        this.type = builder.type;
        this.title = builder.title;
        this.status = builder.status;
        this.detail = builder.detail;
        this.instance = builder.instance;
        this.extensions = new HashMap<>(builder.extensions);
    }
    
    /**
     * Creates a new builder.
     * 
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a standard rate limit exceeded problem detail.
     * 
     * @param instance the URI of the request that was rate limited
     * @param retryAfterSeconds seconds until retry is allowed
     * @return the problem detail
     */
    public static RateLimitProblemDetail tooManyRequests(String instance, long retryAfterSeconds) {
        return builder()
            .type(TYPE_TOO_MANY_REQUESTS)
            .title(TITLE_TOO_MANY_REQUESTS)
            .status(STATUS_TOO_MANY_REQUESTS)
            .detail("Quota exceeded for the current window. Please try again in " + 
                   retryAfterSeconds + " seconds.")
            .instance(instance)
            .extension("retry_after", retryAfterSeconds)
            .build();
    }
    
    /**
     * Converts to a map for JSON serialization.
     * 
     * @return the map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("title", title);
        map.put("status", status);
        
        if (detail != null) {
            map.put("detail", detail);
        }
        
        if (instance != null) {
            map.put("instance", instance);
        }
        
        // Add extension fields
        map.putAll(extensions);
        
        return map;
    }
    
    public String getType() {
        return type;
    }
    
    public String getTitle() {
        return title;
    }
    
    public int getStatus() {
        return status;
    }
    
    public String getDetail() {
        return detail;
    }
    
    public String getInstance() {
        return instance;
    }
    
    public Map<String, Object> getExtensions() {
        return new HashMap<>(extensions);
    }
    
    public Object getExtension(String key) {
        return extensions.get(key);
    }
    
    /**
     * Gets the retry-after value in seconds.
     * 
     * @return seconds until retry is allowed, or null if not set
     */
    public Long getRetryAfter() {
        Object value = extensions.get("retry_after");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
    
    /**
     * Converts this problem detail to a JSON string.
     * 
     * @return JSON representation
     */
    public String toJson() {
        Map<String, Object> map = toMap();
        
        // Simple JSON serialization without dependencies
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value == null) {
                json.append("null");
            } else {
                json.append("\"").append(value.toString()).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Escapes special characters in JSON strings according to RFC 8259.
     *
     * <p>Escapes all control characters and special JSON characters:
     * <ul>
     *   <li>Backslash (\)</li>
     *   <li>Double quote (")</li>
     *   <li>Newline (\n)</li>
     *   <li>Carriage return (\r)</li>
     *   <li>Tab (\t)</li>
     *   <li>Backspace (\b)</li>
     *   <li>Form feed (\f)</li>
     *   <li>Control characters (U+0000 to U+001F)</li>
     * </ul>
     */
    private String escapeJson(String str) {
        if (str == null) {
            return null;
        }

        StringBuilder escaped = new StringBuilder(str.length() + 16);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                default:
                    // Escape control characters (U+0000 to U+001F)
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
    
    /**
     * Creates a problem detail for rate limit exceeded.
     * 
     * @param limiterName the name of the rate limiter
     * @param instance the request URI
     * @param retryAfterSeconds seconds until retry is allowed
     * @return the problem detail
     */
    public static RateLimitProblemDetail forRateLimitExceeded(
            String limiterName, String instance, long retryAfterSeconds) {
        return builder()
            .type(TYPE_TOO_MANY_REQUESTS)
            .title(TITLE_TOO_MANY_REQUESTS)
            .status(STATUS_TOO_MANY_REQUESTS)
            .detail("Rate limit exceeded for '" + limiterName + "'. Please try again in " + retryAfterSeconds + " seconds.")
            .instance(instance)
            .extension("retry_after", retryAfterSeconds)
            .extension("limiter", limiterName)
            .build();
    }
    
    /**
     * Builder for {@link RateLimitProblemDetail}.
     */
    public static class Builder {
        private String type = TYPE_TOO_MANY_REQUESTS;
        private String title = TITLE_TOO_MANY_REQUESTS;
        private int status = STATUS_TOO_MANY_REQUESTS;
        private String detail;
        private String instance;
        private Map<String, Object> extensions = new HashMap<>();
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder status(int status) {
            this.status = status;
            return this;
        }
        
        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }
        
        public Builder instance(String instance) {
            this.instance = instance;
            return this;
        }
        
        /**
         * Adds a custom extension field.
         * 
         * <p>Common extensions for rate limiting:
         * <ul>
         *   <li>retry_after: seconds until retry</li>
         *   <li>limit: the rate limit threshold</li>
         *   <li>remaining: remaining quota</li>
         *   <li>reset: Unix timestamp when quota resets</li>
         * </ul>
         * 
         * @param key the extension field name
         * @param value the extension field value
         * @return this builder
         */
        public Builder extension(String key, Object value) {
            this.extensions.put(key, value);
            return this;
        }
        
        /**
         * Sets standard rate limiting extension fields.
         * 
         * @param limit the rate limit
         * @param remaining the remaining quota
         * @param retryAfter seconds until retry
         * @param reset Unix timestamp when quota resets
         * @return this builder
         */
        public Builder rateLimitFields(int limit, int remaining, long retryAfter, long reset) {
            return extension("limit", limit)
                   .extension("remaining", remaining)
                   .extension("retry_after", retryAfter)
                   .extension("reset", reset);
        }
        
        public RateLimitProblemDetail build() {
            return new RateLimitProblemDetail(this);
        }
    }
}
