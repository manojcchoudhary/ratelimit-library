package com.lycosoft.ratelimit.quarkus.interceptor;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.http.RateLimitProblemDetail;
import com.lycosoft.ratelimit.network.AdaptiveThrottler;
import com.lycosoft.ratelimit.network.TrustedProxyResolver;
import com.lycosoft.ratelimit.quarkus.annotation.RateLimit;
import com.lycosoft.ratelimit.quarkus.annotation.RateLimits;
import com.lycosoft.ratelimit.spi.KeyResolver;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/**
 * CDI interceptor for Quarkus that processes {@link RateLimit} annotations.
 * 
 * <p>This interceptor:
 * <ol>
 *   <li>Extracts rate limit configuration from annotations</li>
 *   <li>Builds context from request data (Vert.x HTTP request)</li>
 *   <li>Resolves keys using SpEL (if needed)</li>
 *   <li>Checks rate limits via {@link LimiterEngine}</li>
 *   <li>Allows or denies the request</li>
 * </ol>
 * 
 * <p><b>Quarkus-specific:</b>
 * <ul>
 *   <li>Uses {@link SecurityIdentity} for principal</li>
 *   <li>Uses Vert.x {@link HttpServerRequest} for HTTP data</li>
 *   <li>CDI injection instead of Spring AOP</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@RateLimit
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class RateLimitInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    
    @Inject
    LimiterEngine limiterEngine;
    
    @Inject
    KeyResolver keyResolver;
    
    @Inject
    SecurityIdentity securityIdentity;
    
    /**
     * Optional HTTP request (may not be available in all contexts).
     */
    @Inject
    io.vertx.core.http.HttpServerRequest httpRequest;
    
    /**
     * Trusted proxy resolver for hop-counting IP resolution.
     * @since 1.1.0
     */
    @Inject
    TrustedProxyResolver proxyResolver;
    
    /**
     * Optional adaptive throttler for soft limits.
     * @since 1.1.0
     */
    @Inject
    jakarta.enterprise.inject.Instance<AdaptiveThrottler> adaptiveThrottlerInstance;
    
    /**
     * Intercepts method invocations with {@link RateLimit} annotation(s).
     * 
     * @param ctx the invocation context
     * @return the method result
     * @throws Exception if rate limit exceeded or method execution fails
     */
    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        Method method = ctx.getMethod();
        
        // Collect all @RateLimit annotations
        List<RateLimit> rateLimits = new ArrayList<>();
        
        // Check for single @RateLimit
        RateLimit single = method.getAnnotation(RateLimit.class);
        if (single != null) {
            rateLimits.add(single);
        }
        
        // Check for @RateLimits (multiple)
        RateLimits multiple = method.getAnnotation(RateLimits.class);
        if (multiple != null) {
            rateLimits.addAll(Arrays.asList(multiple.value()));
        }
        
        // If no annotations, proceed normally
        if (rateLimits.isEmpty()) {
            return ctx.proceed();
        }
        
        // Build context
        RateLimitContext context = buildContext(ctx);
        
        // Check each rate limit
        for (RateLimit rateLimit : rateLimits) {
            RateLimitConfig config = buildConfig(rateLimit, method);
            
            // Build context with this limit's key expression
            RateLimitContext limitContext = RateLimitContext.builder()
                .principal(context.getPrincipal())
                .remoteAddress(context.getRemoteAddress())
                .requestHeaders(context.getRequestHeaders())
                .methodArguments(context.getMethodArguments())
                .methodSignature(context.getMethodSignature())
                .keyExpression(rateLimit.key())
                .build();
            
            // Check rate limit
            RateLimitDecision decision = limiterEngine.tryAcquire(limitContext, config);
            
            if (!decision.isAllowed()) {
                logger.warn("Rate limit exceeded: limiter={}, limit={}/{}{}", 
                           config.getName(),
                           config.getRequests(),
                           config.getWindow(),
                           config.getWindowUnit());
                
                throw new RateLimitExceededException(
                    "Rate limit exceeded for " + config.getName(),
                    decision
                );
            }
            
            // Apply adaptive throttling delay if configured
            if (decision.getDelayMs() > 0) {
                applyAdaptiveDelay(decision.getDelayMs(), config.getName());
            }
            
            logger.trace("Rate limit passed: limiter={}, remaining={}", 
                        config.getName(), decision.getRemaining());
        }
        
        // All rate limits passed - proceed
        return ctx.proceed();
    }
    
    /**
     * Builds rate limit context from the current invocation.
     * 
     * @param ctx the invocation context
     * @return the rate limit context
     */
    private RateLimitContext buildContext(InvocationContext ctx) {
        RateLimitContext.Builder builder = RateLimitContext.builder();
        
        // Method arguments
        builder.methodArguments(ctx.getParameters());
        
        // Principal (from Quarkus SecurityIdentity)
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            builder.principal(securityIdentity.getPrincipal());
        }
        
        // HTTP request data (if available)
        if (httpRequest != null) {
            // Remote address
            String remoteAddr = getClientIpAddress(httpRequest);
            builder.remoteAddress(remoteAddr);
            
            // Request headers
            Map<String, String> headers = new HashMap<>();
            httpRequest.headers().forEach(entry -> 
                headers.put(entry.getKey(), entry.getValue())
            );
            builder.requestHeaders(headers);
        }
        
        return builder.build();
    }
    
    /**
     * Gets the client IP address using trusted proxy resolution.
     * Falls back to IpAddressUtils if proxy resolver is not properly configured.
     * 
     * @param request the Vert.x HTTP request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServerRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String remoteAddr = request.remoteAddress() != null 
            ? request.remoteAddress().host() 
            : "unknown";
        
        // Use trusted proxy resolver for hop counting (secure method)
        String resolvedIp = proxyResolver.resolveClientIp(xForwardedFor, remoteAddr);
        
        // If proxy resolver returns remote address unchanged and we have proxy headers,
        // we would need to adapt HttpServletRequest for IpAddressUtils
        // For Quarkus/Vert.x, stick with proxy resolver only
        return resolvedIp;
    }
    
    /**
     * Builds rate limit configuration from annotation.
     * 
     * @param rateLimit the rate limit annotation
     * @param method the intercepted method
     * @return the rate limit configuration
     */
    private RateLimitConfig buildConfig(RateLimit rateLimit, Method method) {
        // Determine name
        String name = rateLimit.name();
        if (name.isEmpty()) {
            name = method.getDeclaringClass().getName() + "." + method.getName();
        }
        
        // Build configuration
        RateLimitConfig.Builder builder = RateLimitConfig.builder()
            .name(name)
            .algorithm(rateLimit.algorithm())
            .requests(rateLimit.requests())
            .window(rateLimit.window())
            .windowUnit(rateLimit.windowUnit())
            .failStrategy(rateLimit.failStrategy());
        
        // Optional: capacity and refill rate
        if (rateLimit.capacity() > 0) {
            builder.capacity(rateLimit.capacity());
        }
        if (rateLimit.refillRate() > 0) {
            builder.refillRate(rateLimit.refillRate());
        }
        
        return builder.build();
    }
    
    /**
     * Masks a key for logging (to protect PII).
     * 
     * @param key the key to mask
     * @return the masked key
     */
    private String maskKey(String key) {
        if (key == null || key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
    
    /**
     * Applies adaptive throttling delay.
     * 
     * <p>WARNING: This uses Thread.sleep() which can lead to thread pool exhaustion
     * in high-traffic scenarios. For production use with high concurrency, consider
     * using reactive/async delays instead (e.g., with Mutiny Uni.onItem().delayIt()).
     * 
     * @param delayMs delay in milliseconds
     * @param limiterName the limiter name (for logging)
     * @since 1.1.0
     */
//    private void applyAdaptiveDelay(long delayMs, String limiterName) {
//        if (adaptiveThrottlerInstance.isResolvable() && !adaptiveThrottlerInstance.isUnsatisfied()) {
//            try {
//                logger.debug("Applying adaptive throttle delay: {}ms for limiter '{}'",
//                            delayMs, limiterName);
//                Thread.sleep(delayMs);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.warn("Adaptive throttle delay interrupted for limiter '{}'", limiterName);
//            }
//        }
//    }
    private Uni<Object> applyAdaptiveDelay(long delayMs, String limiterName) {
        if (delayMs > 0) {
            // Non-blocking delay
            return Uni.createFrom().item(new Object())
                    .onItem().delayIt().by(Duration.ofMillis(delayMs));
        }
        return Uni.createFrom().item(new Object());
    }
    
    /**
     * Exception thrown when rate limit is exceeded.
     */
    public static class RateLimitExceededException extends RuntimeException {
        private final RateLimitDecision decision;
        private final String instance;  // Request path for RFC 9457
        
        public RateLimitExceededException(String message, RateLimitDecision decision) {
            this(message, decision, null);
        }
        
        public RateLimitExceededException(String message, RateLimitDecision decision, String instance) {
            super(message);
            this.decision = decision;
            this.instance = instance;
        }
        
        public RateLimitDecision getDecision() {
            return decision;
        }
        
        public String getInstance() {
            return instance;
        }
    }
}
