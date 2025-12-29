package com.lycosoft.ratelimit.spi;

import com.lycosoft.ratelimit.engine.RateLimitContext;

/**
 * Service Provider Interface for resolving rate limit keys.
 * 
 * <p>The key resolver is responsible for identifying the unique caller/entity
 * that should be rate limited. Common strategies include:
 * <ul>
 *   <li>IP address: Rate limit by remote IP</li>
 *   <li>User ID: Rate limit by authenticated user</li>
 *   <li>API Key: Rate limit by API token</li>
 *   <li>Composite: Combine multiple identifiers (e.g., "tenant:user:endpoint")</li>
 * </ul>
 * 
 * <p><b>Security:</b> Key resolution may involve expression evaluation (SpEL, EL).
 * Implementations MUST use restricted evaluation contexts to prevent injection attacks.
 * 
 * <p><b>Null Handling:</b> If key resolution fails or returns null, implementations
 * should map to a "global-anonymous" bucket to prevent service crashes.
 * 
 * @since 1.0.0
 */
public interface KeyResolver {
    
    /**
     * Resolves the rate limit key for the given context.
     * 
     * <p>The key uniquely identifies the entity being rate limited.
     * For composite keys, use a separator like ":" (e.g., "tenant:user:endpoint").
     * 
     * <p><b>Performance:</b> This method is called on every request, so it must be fast.
     * For SpEL/EL implementations, expressions should be compiled and cached.
     * 
     * @param context the request context containing principal, IP, arguments, etc.
     * @return the resolved key (never null)
     * @throws KeyResolutionException if key resolution fails
     */
    String resolveKey(RateLimitContext context) throws KeyResolutionException;
    
    /**
     * Exception thrown when key resolution fails.
     */
    class KeyResolutionException extends RuntimeException {
        public KeyResolutionException(String message) {
            super(message);
        }
        
        public KeyResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
