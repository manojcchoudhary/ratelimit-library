package com.lycosoft.ratelimit.quarkus.annotation;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.constants.RateLimitDefaultValue;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Annotation to apply rate limiting to Spring methods.
 * 
 * <p>This annotation can be placed on methods or classes. Method-level annotations
 * override class-level defaults.
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * @RestController
 * public class OrderController {
 *     
 *     @RateLimit(
 *         key = "#user.id",
 *         requests = 100,
 *         window = 60,
 *         windowUnit = TimeUnit.SECONDS
 *     )
 *     @PostMapping("/orders")
 *     public Order createOrder(@AuthenticationPrincipal User user, @RequestBody OrderRequest req) {
 *         return orderService.create(req);
 *     }
 * }
 * }</pre>
 * 
 * <p><b>SpEL Support:</b>
 * The {@code key} attribute supports Spring Expression Language (SpEL):
 * <ul>
 *   <li>{@code #user.id} - Extract user ID from parameter</li>
 *   <li>{@code #ip} - Use remote IP address</li>
 *   <li>{@code #args[0]} - First method argument</li>
 *   <li>{@code #headers['X-API-Key']} - HTTP header value</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimits.class)
@InterceptorBinding
public @interface RateLimit {
    
    /**
     * The name of this rate limiter.
     * 
     * <p>If not specified, defaults to the fully qualified method name.
     * 
     * @return the rate limiter name
     */
    @jakarta.enterprise.util.Nonbinding
    String name() default "";
    
    /**
     * The key expression for identifying the rate limit subject.
     * 
     * <p>Supports both static keys and SpEL expressions:
     * <ul>
     *   <li>Static: {@code "global"}, {@code "api-endpoint"}</li>
     *   <li>SpEL: {@code "#user.id"}, {@code "#ip"}, {@code "#args[0].customerId"}</li>
     * </ul>
     * 
     * <p><b>Available Variables:</b>
     * <ul>
     *   <li>{@code #user} - The authenticated principal (if available)</li>
     *   <li>{@code #ip} - The remote IP address</li>
     *   <li>{@code #args} - Array of method arguments</li>
     *   <li>{@code #headers} - Map of HTTP request headers</li>
     * </ul>
     * 
     * @return the key expression
     */
    @jakarta.enterprise.util.Nonbinding
    String key() default RateLimitDefaultValue.KEY_EXPRESSION;
    
    /**
     * The rate limiting algorithm to use.
     * 
     * @return the algorithm
     */
    @jakarta.enterprise.util.Nonbinding
    RateLimitConfig.Algorithm algorithm() default RateLimitConfig.Algorithm.TOKEN_BUCKET;
    
    /**
     * The maximum number of requests allowed.
     * 
     * @return the request limit
     */
    @jakarta.enterprise.util.Nonbinding
    int requests() default -1;
    
    /**
     * The time window duration.
     * 
     * @return the window duration
     */
    @jakarta.enterprise.util.Nonbinding
    long window() default -1;
    
    /**
     * The time unit for the window.
     * 
     * @return the time unit
     */
    @jakarta.enterprise.util.Nonbinding
    TimeUnit windowUnit() default TimeUnit.SECONDS;
    
    /**
     * The failure strategy when storage is unavailable.
     * 
     * <p>Defaults to {@code FAIL_OPEN} (allow requests).
     * 
     * @return the failure strategy
     */
    @jakarta.enterprise.util.Nonbinding
    RateLimitConfig.FailStrategy failStrategy() default RateLimitConfig.FailStrategy.FAIL_OPEN;
    
    /**
     * The bucket capacity for Token Bucket algorithm.
     * 
     * <p>If not specified, defaults to {@code requests}.
     * 
     * @return the bucket capacity
     */
    @jakarta.enterprise.util.Nonbinding
    int capacity() default -1;
    
    /**
     * The refill rate for Token Bucket algorithm (tokens per second).
     * 
     * <p>If not specified, auto-calculated from {@code requests} and {@code window}.
     * 
     * @return the refill rate
     */
    @jakarta.enterprise.util.Nonbinding
    double refillRate() default -1.0;
}
