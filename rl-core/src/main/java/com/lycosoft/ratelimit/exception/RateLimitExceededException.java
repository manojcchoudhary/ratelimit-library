package com.lycosoft.ratelimit.exception;

import com.lycosoft.ratelimit.engine.RateLimitDecision;

/**
 * Exception thrown when rate limit is exceeded.
 * 
 * <p>This exception carries RFC 9457 "Problem Details for HTTP APIs" metadata
 * for standardized error responses.
 * 
 * <p><b>RFC 9457 Format:</b>
 * <pre>{@code
 * {
 *   "type": "https://ratelimit.io/probs/too-many-requests",
 *   "title": "Too Many Requests",
 *   "status": 429,
 *   "detail": "Quota exceeded for the current window. Please try again in 24 seconds.",
 *   "instance": "/api/v1/orders",
 *   "retry_after": 24
 * }
 * }</pre>
 * 
 * @since 1.0.0
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final RateLimitDecision decision;
    private final String instance;  // The request path (e.g., "/api/v1/orders")
    
    /**
     * Creates a rate limit exceeded exception.
     * 
     * @param message the error message
     * @param decision the rate limit decision
     */
    public RateLimitExceededException(String message, RateLimitDecision decision) {
        this(message, decision, null);
    }
    
    /**
     * Creates a rate limit exceeded exception with instance path.
     * 
     * @param message the error message
     * @param decision the rate limit decision
     * @param instance the request path (e.g., "/api/v1/orders")
     */
    public RateLimitExceededException(String message, RateLimitDecision decision, String instance) {
        super(message);
        this.decision = decision;
        this.instance = instance;
    }
    
    /**
     * Gets the rate limit decision that triggered this exception.
     * 
     * @return the decision
     */
    public RateLimitDecision getDecision() {
        return decision;
    }
    
    /**
     * Gets the request path (for RFC 9457 instance field).
     * 
     * @return the request path, or null
     */
    public String getInstance() {
        return instance;
    }
    
    /**
     * Gets the RFC 9457 problem type URI.
     * 
     * @return the problem type URI
     */
    public String getProblemType() {
        return "https://ratelimit.io/probs/too-many-requests";
    }
    
    /**
     * Gets the RFC 9457 title.
     * 
     * @return the title
     */
    public String getProblemTitle() {
        return "Too Many Requests";
    }
    
    /**
     * Gets the HTTP status code.
     * 
     * @return 429
     */
    public int getStatus() {
        return 429;
    }
    
    /**
     * Gets the RFC 9457 detail message.
     * 
     * @return the detail message
     */
    public String getProblemDetail() {
        long retryAfter = decision.getRetryAfterSeconds();
        return String.format(
            "Quota exceeded for the current window. Please try again in %d seconds.",
            retryAfter
        );
    }
    
    /**
     * Gets the Retry-After value in seconds.
     * 
     * @return seconds until retry
     */
    public long getRetryAfter() {
        return decision.getRetryAfterSeconds();
    }
    
    /**
     * Gets the RateLimit-Policy header value.
     * 
     * <p>Format: {@code <limit>;w=<window>} (e.g., "100;w=60")
     * 
     * @return the policy string
     */
    public String getRateLimitPolicy() {
        long windowSeconds = (decision.getResetTime() - System.currentTimeMillis()) / 1000;
        return String.format("%d;w=%d", decision.getLimit(), Math.max(1, windowSeconds));
    }
    
    /**
     * Converts to RFC 9457 Problem Details JSON.
     * 
     * @return JSON string
     */
    public String toProblemDetailsJson() {
        return String.format(
            "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"instance\":\"%s\",\"retry_after\":%d}",
            getProblemType(),
            getProblemTitle(),
            getStatus(),
            getProblemDetail().replace("\"", "\\\""),
            instance != null ? instance : "",
            getRetryAfter()
        );
    }
}
