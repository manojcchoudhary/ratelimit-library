package com.lycosoft.ratelimit.storage;

import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.spi.KeyResolver;

/**
 * Simple key resolver that returns a static key.
 * 
 * <p>This is useful for:
 * <ul>
 *   <li>Testing</li>
 *   <li>Global rate limiting (all requests use same bucket)</li>
 *   <li>Simple use cases where expression evaluation is not needed</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class StaticKeyResolver implements KeyResolver {
    
    private final String staticKey;
    
    /**
     * Creates a key resolver that always returns the same key.
     * 
     * @param staticKey the key to return
     */
    public StaticKeyResolver(String staticKey) {
        this.staticKey = staticKey != null ? staticKey : "global";
    }
    
    /**
     * Creates a key resolver that uses "global" as the key.
     */
    public StaticKeyResolver() {
        this("global");
    }
    
    @Override
    public String resolveKey(RateLimitContext context) {
        return staticKey;
    }
}
