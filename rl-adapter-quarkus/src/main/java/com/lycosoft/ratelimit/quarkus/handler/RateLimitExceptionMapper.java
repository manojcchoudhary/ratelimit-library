package com.lycosoft.ratelimit.quarkus.handler;

import com.lycosoft.ratelimit.http.RateLimitProblemDetail;
import com.lycosoft.ratelimit.quarkus.interceptor.RateLimitInterceptor;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global exception handler for rate limit exceptions with RFC 9457 support (Quarkus).
 * 
 * <p>This handler converts {@link RateLimitInterceptor.RateLimitExceededException}
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
@Provider
public class RateLimitExceptionMapper 
    implements ExceptionMapper<RateLimitInterceptor.RateLimitExceededException> {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitExceptionMapper.class);
    
    @Override
    public Response toResponse(RateLimitInterceptor.RateLimitExceededException ex) {
        var decision = ex.getDecision();
        
        // Get or create Problem Detail
        RateLimitProblemDetail problemDetail;
        if (decision.getProblemDetail() != null) {
            problemDetail = decision.getProblemDetail();
        } else {
            // Create default problem detail
            int retryAfter = (int) decision.getRetryAfterSeconds();
            String instance = ex.getInstance();
            problemDetail = RateLimitProblemDetail.forRateLimitExceeded(
                decision.getLimiterName(),
                instance != null ? instance : "/unknown",
                retryAfter
            );
        }
        
        logger.debug("Rate limit exceeded: limiter={}, retry_after={}s", 
                    decision.getLimiterName(), problemDetail.getRetryAfter());
        
        // Build RFC 7231 compliant response
        return Response
            .status(Response.Status.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(problemDetail.getRetryAfter()))
            .header("RateLimit-Policy", buildRateLimitPolicy(decision))
            .header("X-RateLimit-Limit", String.valueOf(decision.getLimit()))
            .header("X-RateLimit-Remaining", "0")
            .header("X-RateLimit-Reset", String.valueOf(decision.getResetTime() / 1000))
            .type("application/problem+json")
            .entity(problemDetail.toJson())
            .build();
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
