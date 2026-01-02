package com.lycosoft.ratelimit.example.webflux.filter;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Non-blocking rate limit filter for WebFlux.
 * 
 * <p>Applies rate limiting to /reactive/* paths without blocking threads.
 */
@Component
public class RateLimitWebFilter implements WebFilter {
    
    private final LimiterEngine limiterEngine;
    
    public RateLimitWebFilter(LimiterEngine limiterEngine) {
        this.limiterEngine = limiterEngine;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Only apply to /reactive/* paths
        if (!path.startsWith("/reactive/")) {
            return chain.filter(exchange);
        }
        
        // Get client IP (non-blocking)
        String clientIp = getClientIp(exchange);
        
        // Build rate limit config for this path (SLIDING_WINDOW for strict limiting)
        RateLimitConfig config = RateLimitConfig.builder()
            .name("reactive-" + path.replaceAll("/", "-"))
            .requests(10)
            .window(60)
            .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
            .build();
        
        // Build context
        RateLimitContext context = RateLimitContext.builder()
            .remoteAddress(clientIp)
            .keyExpression(clientIp)
            .build();
        
        // Check rate limit (non-blocking!)
        return Mono.fromCallable(() -> limiterEngine.checkLimit(clientIp, config, context))
            .flatMap(decision -> {
                if (decision.isAllowed()) {
                    // Add rate limit headers
                    exchange.getResponse().getHeaders()
                        .add("X-RateLimit-Limit", String.valueOf(decision.getLimit()));
                    exchange.getResponse().getHeaders()
                        .add("X-RateLimit-Remaining", String.valueOf(decision.getRemaining()));
                    
                    // Continue chain
                    return chain.filter(exchange);
                } else {
                    // Return 429
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders()
                        .add("Retry-After", String.valueOf(decision.getRetryAfterSeconds()));
                    exchange.getResponse().getHeaders()
                        .add("X-RateLimit-Limit", String.valueOf(decision.getLimit()));
                    exchange.getResponse().getHeaders()
                        .add("X-RateLimit-Remaining", "0");
                    
                    return exchange.getResponse().setComplete();
                }
            });
    }
    
    private String getClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getHostString()
            : "unknown";
    }
}
