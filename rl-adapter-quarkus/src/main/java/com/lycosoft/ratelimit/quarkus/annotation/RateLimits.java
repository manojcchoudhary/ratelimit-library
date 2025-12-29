package com.lycosoft.ratelimit.quarkus.annotation;

import java.lang.annotation.*;

/**
 * Container annotation for multiple {@link RateLimit} annotations.
 * 
 * <p>This allows applying multiple rate limits to a single method (tiered limiting).
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * @RateLimits({
 *     @RateLimit(name = "short-burst", requests = 10, window = 1, windowUnit = TimeUnit.SECONDS),
 *     @RateLimit(name = "long-term", requests = 1000, window = 1, windowUnit = TimeUnit.HOURS)
 * })
 * @GetMapping("/api/data")
 * public Data getData() {
 *     return dataService.fetchData();
 * }
 * }</pre>
 * 
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimits {
    
    /**
     * The rate limit annotations.
     * 
     * @return the rate limits
     */
    RateLimit[] value();
}
