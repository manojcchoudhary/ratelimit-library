package com.lycosoft.ratelimit.http;

import java.util.HashMap;
import java.util.Map;

/**
 * RFC 9457 Problem Details for HTTP APIs.
 * 
 * <p>Provides a standardized, machine-readable format for HTTP error responses.
 * 
 * <p><b>RFC 9457 Standard:</b>
 * <pre>
 * {
 *   "type": "https://ratelimit.io/probs/too-many-requests",
 *   "title": "Too Many Requests",
 *   "status": 429,
 *   "detail": "Quota exceeded for the current window. Please try again in 24 seconds.",
 *   "instance": "/api/v1/orders",
 *   "retry_after": 24
 * }
 * </pre>
 * 
 * <p><b>Benefits:</b>
 * <ul>
 *   <li>Machine-readable error responses</li>
 *   <li>Consistent error format across APIs</li>
 *   <li>Standardized problem categorization</li>
 *   <li>Extension properties for custom data</li>
 * </ul>
 * 
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457.html">RFC 9457</a>
 * @since 1.0.0
 */
public class RateLimitProblemDetails {
    
    /**
     * Default problem type URI for rate limit errors.
     */
    public static final String DEFAULT_TYPE_URI = "https://ratelimit.io/probs/too-many-requests";
    
    /**
     * A URI reference that identifies the problem type.
     */
    private String type;
    
    /**
     * A short, human-readable summary of the problem type.
     */
    private String title;
    
    /**
     * The HTTP status code.
     */
    private int status;
    
    /**
     * A human-readable explanation specific to this occurrence.
     */
    private String detail;
    
    /**
     * A URI reference that identifies the specific occurrence.
     */
    private String instance;
    
    /**
     * Extension properties (e.g., retry_after, limit, remaining).
     */
    private Map<String, Object> extensions;
    
    /**
     * Creates problem details with default type.
     */
    public RateLimitProblemDetails() {
        this.type = DEFAULT_TYPE_URI;
        this.title = "Too Many Requests";
        this.status = 429;
        this.extensions = new HashMap<>();
    }
    
    /**
     * Creates a builder for problem details.
     * 
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates standard rate limit problem details.
     * 
     * @param detail the specific error detail
     * @param instance the request URI
     * @param retryAfter seconds until retry allowed
     * @return the problem details
     */
    public static RateLimitProblemDetails rateLimitExceeded(String detail, String instance, long retryAfter) {
        return builder()
            .type(DEFAULT_TYPE_URI)
            .title("Too Many Requests")
            .status(429)
            .detail(detail)
            .instance(instance)
            .extension("retry_after", retryAfter)
            .build();
    }
    
    // Getters and setters
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public String getDetail() {
        return detail;
    }
    
    public void setDetail(String detail) {
        this.detail = detail;
    }
    
    public String getInstance() {
        return instance;
    }
    
    public void setInstance(String instance) {
        this.instance = instance;
    }
    
    public Map<String, Object> getExtensions() {
        return extensions;
    }
    
    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
    
    /**
     * Adds an extension property.
     * 
     * @param name the property name
     * @param value the property value
     */
    public void addExtension(String name, Object value) {
        if (extensions == null) {
            extensions = new HashMap<>();
        }
        extensions.put(name, value);
    }
    
    /**
     * Builder for problem details.
     */
    public static class Builder {
        private final RateLimitProblemDetails details;
        
        private Builder() {
            this.details = new RateLimitProblemDetails();
        }
        
        public Builder type(String type) {
            details.type = type;
            return this;
        }
        
        public Builder title(String title) {
            details.title = title;
            return this;
        }
        
        public Builder status(int status) {
            details.status = status;
            return this;
        }
        
        public Builder detail(String detail) {
            details.detail = detail;
            return this;
        }
        
        public Builder instance(String instance) {
            details.instance = instance;
            return this;
        }
        
        public Builder extension(String name, Object value) {
            details.addExtension(name, value);
            return this;
        }
        
        public Builder extensions(Map<String, Object> extensions) {
            details.extensions = new HashMap<>(extensions);
            return this;
        }
        
        public RateLimitProblemDetails build() {
            return details;
        }
    }
}
