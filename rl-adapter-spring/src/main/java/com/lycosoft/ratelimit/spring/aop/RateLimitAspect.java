package com.lycosoft.ratelimit.spring.aop;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.network.AdaptiveThrottler;
import com.lycosoft.ratelimit.network.TrustedProxyResolver;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import com.lycosoft.ratelimit.spring.annotation.RateLimits;
import com.lycosoft.ratelimit.spring.util.IpAddressUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Spring AOP aspect that intercepts methods annotated with {@link RateLimit}.
 * 
 * <p>This aspect:
 * <ol>
 *   <li>Extracts rate limit configuration from annotation</li>
 *   <li>Builds rate limit context from request data</li>
 *   <li>Resolves the rate limit key using SpEL</li>
 *   <li>Checks the rate limit via {@link LimiterEngine}</li>
 *   <li>Allows or denies the request</li>
 * </ol>
 * 
 * <p><b>Execution Order:</b>
 * For multiple {@code @RateLimit} annotations, all limits must pass for the request to proceed.
 * 
 * @since 1.0.0
 */
@Aspect
@Component
public class RateLimitAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);
    
    private final LimiterEngine limiterEngine;
    private final KeyResolver keyResolver;
    private final TrustedProxyResolver proxyResolver;  // NEW
    private final AdaptiveThrottler adaptiveThrottler;  // NEW (optional)
    
    /**
     * Creates a rate limit aspect.
     * 
     * @param limiterEngine the limiter engine
     * @param keyResolver the key resolver
     */
    public RateLimitAspect(LimiterEngine limiterEngine, KeyResolver keyResolver) {
        this(limiterEngine, keyResolver, null, null);
    }
    
    /**
     * Creates a rate limit aspect with advanced features.
     * 
     * @param limiterEngine the limiter engine
     * @param keyResolver the key resolver
     * @param proxyResolver the trusted proxy resolver (optional)
     * @param adaptiveThrottler the adaptive throttler (optional)
     * @since 1.1.0
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public RateLimitAspect(LimiterEngine limiterEngine, 
                          KeyResolver keyResolver,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) TrustedProxyResolver proxyResolver,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) AdaptiveThrottler adaptiveThrottler) {
        this.limiterEngine = limiterEngine;
        this.keyResolver = keyResolver;
        this.proxyResolver = proxyResolver != null ? proxyResolver : new TrustedProxyResolver();
        this.adaptiveThrottler = adaptiveThrottler;
        logger.info("RateLimitAspect initialized (proxy resolver: {}, adaptive throttling: {})",
                   proxyResolver != null, adaptiveThrottler != null);
    }
    
    /**
     * Intercepts methods with single {@code @RateLimit} annotation.
     * 
     * @param joinPoint the join point
     * @param rateLimit the rate limit annotation
     * @return the method result
     * @throws Throwable if the method execution fails
     */
    @Around("@annotation(rateLimit)")
    public Object rateLimitSingle(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        return checkRateLimits(joinPoint, Collections.singletonList(rateLimit));
    }
    
    /**
     * Intercepts methods with multiple {@code @RateLimit} annotations.
     * 
     * @param joinPoint the join point
     * @param rateLimits the rate limits container
     * @return the method result
     * @throws Throwable if the method execution fails
     */
    @Around("@annotation(rateLimits)")
    public Object rateLimitMultiple(ProceedingJoinPoint joinPoint, RateLimits rateLimits) throws Throwable {
        return checkRateLimits(joinPoint, Arrays.asList(rateLimits.value()));
    }
    
    /**
     * Checks all rate limits for the method invocation.
     * 
     * @param joinPoint the join point
     * @param rateLimits the list of rate limit annotations
     * @return the method result
     * @throws Throwable if the method execution fails or rate limit exceeded
     */
    private Object checkRateLimits(ProceedingJoinPoint joinPoint, List<RateLimit> rateLimits) throws Throwable {
        // Build context from current request
        RateLimitContext context = buildContext(joinPoint);
        
        // Check each rate limit
        for (RateLimit rateLimit : rateLimits) {
            RateLimitConfig config = buildConfig(rateLimit, joinPoint);
            
            // Update context with this limit's key expression
            RateLimitContext limitContext = RateLimitContext.builder()
                .principal(context.getPrincipal())
                .remoteAddress(context.getRemoteAddress())
                .requestHeaders(context.getRequestHeaders())
                .methodArguments(context.getMethodArguments())
                .keyExpression(rateLimit.key())
                .build();
            
            // Resolve the actual key
            String resolvedKey = keyResolver.resolveKey(limitContext);
            
            // Check rate limit
            RateLimitDecision decision = limiterEngine.tryAcquire(limitContext, config);
            
            if (!decision.isAllowed()) {
                logger.warn("Rate limit exceeded: limiter={}, key={}, limit={}/{}{}", 
                           config.getName(),
                           maskKey(resolvedKey),
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
        
        // All rate limits passed - proceed with method execution
        return joinPoint.proceed();
    }
    
    /**
     * Builds rate limit context from the current request.
     * 
     * @param joinPoint the join point
     * @return the rate limit context
     */
    private RateLimitContext buildContext(ProceedingJoinPoint joinPoint) {
        RateLimitContext.Builder builder = RateLimitContext.builder();
        
        // Method arguments
        builder.methodArguments(joinPoint.getArgs());
        
        // Principal (if available from Spring Security)
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                builder.principal(authentication.getPrincipal());
            }
        } catch (Exception e) {
            logger.trace("Failed to get authentication: {}", e.getMessage());
        }
        
        // HTTP request data (if available)
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // Remote address
                String remoteAddr = getClientIpAddress(request);
                builder.remoteAddress(remoteAddr);
                
                // Request headers
                Map<String, String> headers = new HashMap<>();
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    headers.put(headerName, request.getHeader(headerName));
                }
                builder.requestHeaders(headers);
            }
        } catch (Exception e) {
            logger.trace("Failed to get request attributes: {}", e.getMessage());
        }
        
        return builder.build();
    }
    
    /**
     * Gets the client IP address using trusted proxy resolution.
     * Falls back to IpAddressUtils if proxy resolver is not properly configured.
     * 
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String remoteAddr = request.getRemoteAddr();
        
        // Use trusted proxy resolver for hop counting (secure method)
        String resolvedIp = proxyResolver.resolveClientIp(xForwardedFor, remoteAddr);
        
        // If proxy resolver returns remote address unchanged and we have proxy headers,
        // fall back to IpAddressUtils for better header detection
        if (resolvedIp.equals(remoteAddr) && xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Fallback to comprehensive header checking
            return IpAddressUtils.getClientIpAddress(request);
        }
        
        return resolvedIp;
    }
    
    /**
     * Builds rate limit configuration from annotation.
     * 
     * @param rateLimit the rate limit annotation
     * @param joinPoint the join point
     * @return the rate limit configuration
     */
    private RateLimitConfig buildConfig(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        // Determine name
        String name = rateLimit.name();
        if (name.isEmpty()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
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
        
        // Optional: capacity and refill rate (for Token Bucket)
        if (rateLimit.capacity() > 0) {
            builder.capacity(rateLimit.capacity());
        }
        if (rateLimit.refillRate() > 0) {
            builder.refillRate(rateLimit.refillRate());
        }
        
        return builder.build();
    }
    
    /**
     * Applies adaptive throttling delay.
     * 
     * <p>WARNING: This uses Thread.sleep() which can lead to thread pool exhaustion
     * in high-traffic scenarios. For production use with high concurrency, consider
     * using reactive/async delays instead (e.g., with Spring WebFlux).
     * 
     * @param delayMs delay in milliseconds
     * @param limiterName the limiter name (for logging)
     * @since 1.1.0
     */
    private void applyAdaptiveDelay(long delayMs, String limiterName) {
        try {
            logger.debug("Applying adaptive throttle delay: {}ms for limiter '{}'", 
                        delayMs, limiterName);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Adaptive throttle delay interrupted for limiter '{}'", limiterName);
        }
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
        // Show first 4 and last 4 characters
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
    
    /**
     * Exception thrown when rate limit is exceeded.
     */
    public static class RateLimitExceededException extends RuntimeException {
        private final RateLimitDecision decision;
        
        public RateLimitExceededException(String message, RateLimitDecision decision) {
            super(message);
            this.decision = decision;
        }
        
        public RateLimitDecision getDecision() {
            return decision;
        }
    }
}
