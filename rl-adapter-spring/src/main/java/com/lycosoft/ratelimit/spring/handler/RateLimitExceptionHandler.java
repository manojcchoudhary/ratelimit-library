package com.lycosoft.ratelimit.spring.handler;

import com.lycosoft.ratelimit.http.RateLimitProblemDetail;
import com.lycosoft.ratelimit.spring.aop.RateLimitAspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Global exception handler for rate limit exceptions with RFC 9457 support.
 * 
 * <p>This handler converts {@link RateLimitAspect.RateLimitExceededException}
 * into RFC 9457 Problem Details JSON responses with appropriate HTTP headers.
 * 
 * <p><b>Response Headers (RFC 7231):</b>
 * <ul>
 *   <li>{@code Retry-After}: Seconds until retry allowed</li>
 *   <li>{@code RateLimit-Policy}: Applied limit (e.g., "100;w=60")</li>
 *   <li>{@code X-RateLimit-Limit}: Maximum requests allowed</li>
 *   <li>{@code X-RateLimit-Remaining}: Requests remaining (0)</li>
 *   <li>{@code X-RateLimit-Reset}: Unix timestamp when limit resets</li>
 * </ul>
 * 
 * @since 1.1.0
 */
@RestControllerAdvice
public class RateLimitExceptionHandler {
    
    /**
     * Handles rate limit exceeded exceptions.
     * 
     * @param ex the exception
     * @param request the HTTP request
     * @return RFC 9457 Problem Details response with appropriate headers
     */
    @ExceptionHandler(RateLimitAspect.RateLimitExceededException.class)
    public ResponseEntity<String> handleRateLimitExceeded(
            RateLimitAspect.RateLimitExceededException ex,
            HttpServletRequest request) {
        
        var decision = ex.getDecision();
        
        // Get or create Problem Detail
        RateLimitProblemDetail problemDetail;
        if (decision.getProblemDetail() != null) {
            problemDetail = decision.getProblemDetail();
        } else {
            // Create default problem detail
            int retryAfter = (int) decision.getRetryAfterSeconds();
            String instance = request.getRequestURI();
            problemDetail = RateLimitProblemDetail.forRateLimitExceeded(
                decision.getLimiterName(),
                instance,
                retryAfter
            );
        }
        
        // Build response with RFC 7231 headers
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(problemDetail.getRetryAfter()))
            .header("RateLimit-Policy", buildRateLimitPolicy(decision))
            .header("X-RateLimit-Limit", String.valueOf(decision.getLimit()))
            .header("X-RateLimit-Remaining", "0")
            .header("X-RateLimit-Reset", String.valueOf(decision.getResetTime() / 1000))
            .header("Content-Type", "application/problem+json")
            .body(problemDetail.toJson());
    }
    
    /**
     * Builds the RateLimit-Policy header value.
     * 
     * <p>Format: "{limit};w={window}" (e.g., "100;w=60")
     * 
     * @param decision the rate limit decision
     * @return the policy header value
     */
    private String buildRateLimitPolicy(com.lycosoft.ratelimit.engine.RateLimitDecision decision) {
        // Calculate window from reset time (approximate)
        long now = System.currentTimeMillis();
        long window = (decision.getResetTime() - now) / 1000;
        
        return String.format("%d;w=%d", decision.getLimit(), Math.max(1, window));
    }
}
