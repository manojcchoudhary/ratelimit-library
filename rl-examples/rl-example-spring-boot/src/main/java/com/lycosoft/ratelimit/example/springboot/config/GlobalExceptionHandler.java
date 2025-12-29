package com.lycosoft.ratelimit.example.springboot.config;

import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.spring.aop.RateLimitAspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Global exception handler for rate limit exceeded exceptions.
 * 
 * <p>Provides standardized 429 responses with:
 * <ul>
 *   <li>Retry-After header (seconds until reset)</li>
 *   <li>X-RateLimit-* headers (limit, remaining, reset)</li>
 *   <li>JSON error response with details</li>
 * </ul>
 * 
 * <p><b>Example Response:</b>
 * <pre>
 * HTTP/1.1 429 Too Many Requests
 * Retry-After: 45
 * X-RateLimit-Limit: 100
 * X-RateLimit-Remaining: 0
 * X-RateLimit-Reset: 1703635200
 * Content-Type: application/json
 * 
 * {
 *   "error": "Too Many Requests",
 *   "message": "Rate limit exceeded for 'user-profile-basic'",
 *   "path": "/api/user/profile",
 *   "timestamp": "2024-12-27T10:30:00",
 *   "rateLimit": {
 *     "limit": 100,
 *     "remaining": 0,
 *     "reset": 1703635200,
 *     "retryAfter": 45
 *   }
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle rate limit exceeded exceptions.
     * 
     * @param ex the rate limit exceeded exception
     * @param request the HTTP request
     * @return standardized error response with 429 status
     */
    @ExceptionHandler(RateLimitAspect.RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(
            RateLimitAspect.RateLimitExceededException ex,
            HttpServletRequest request) {
        
        RateLimitDecision decision = ex.getDecision();
        long retryAfterSeconds = decision.getRetryAfterSeconds();
        
        Map<String, Object> errorResponse = Map.of(
            "error", "Too Many Requests",
            "message", "Rate limit exceeded for '" + decision.getLimiterName() + "'",
            "path", request.getRequestURI(),
            "timestamp", LocalDateTime.now().toString(),
            "rateLimit", Map.of(
                "limit", decision.getLimit(),
                "remaining", decision.getRemaining(),
                "reset", decision.getResetTime() / 1000,  // Unix timestamp in seconds
                "retryAfter", retryAfterSeconds
            )
        );
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(retryAfterSeconds))
            .header("X-RateLimit-Limit", String.valueOf(decision.getLimit()))
            .header("X-RateLimit-Remaining", String.valueOf(decision.getRemaining()))
            .header("X-RateLimit-Reset", String.valueOf(decision.getResetTime() / 1000))
            .body(errorResponse);
    }
}
