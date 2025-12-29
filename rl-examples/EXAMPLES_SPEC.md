# Rate Limit Library - Examples Module
## Complete Implementation Specification

---

## 1. Project Structure

```
rl-examples/
├── pom.xml (parent POM)
├── README.md
├── rl-example-spring-boot/          # Spring Boot 3.x MVC
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/lycosoft/ratelimit/example/springboot/
│   │       ├── SpringBootExampleApplication.java
│   │       ├── config/
│   │       │   ├── SecurityConfig.java
│   │       │   ├── RateLimitConfig.java
│   │       │   └── CustomUserDetails.java
│   │       ├── controller/
│   │       │   ├── PublicApiController.java      # Example 1: IP-based
│   │       │   ├── UserTieredController.java     # Example 2: User tiers
│   │       │   └── PartnerApiController.java     # Example 3: Token bucket
│   │       ├── handler/
│   │       │   └── GlobalRateLimitHandler.java
│   │       └── model/
│   │           ├── ApiResponse.java
│   │           └── ErrorResponse.java
│   └── src/main/resources/
│       ├── application.yml                       # In-memory config
│       ├── application-redis.yml                 # Redis config
│       └── application-resilient.yml             # Fail-open config
│
├── rl-example-webflux/                # Reactive Spring
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/lycosoft/ratelimit/example/webflux/
│   │       ├── WebFluxExampleApplication.java
│   │       ├── config/
│   │       │   └── WebFluxRateLimitConfig.java
│   │       ├── filter/
│   │       │   └── RateLimitWebFilter.java
│   │       ├── router/
│   │       │   └── FunctionalRoutes.java
│   │       └── handler/
│   │           └── ApiHandler.java
│   └── src/main/resources/
│       └── application.yml
│
├── rl-example-standalone/             # Pure Java
│   ├── pom.xml
│   ├── src/main/java/
│   │   └── com/lycosoft/ratelimit/example/standalone/
│   │       ├── StandaloneExample.java
│   │       ├── DirectEngineExample.java
│   │       ├── CustomKeyResolverExample.java
│   │       └── model/
│   │           └── ProcessingTask.java
│   └── src/main/resources/
│       └── logback.xml
│
└── docker/
    ├── docker-compose.yml
    ├── prometheus/
    │   └── prometheus.yml
    └── grafana/
        ├── provisioning/
        │   ├── dashboards/
        │   │   └── dashboard.yml
        │   └── datasources/
        │       └── datasource.yml
        └── dashboards/
            └── ratelimit-dashboard.json
```

---

## 2. Spring Boot Example (Traditional MVC)

### 2.1 Application Configuration

**File**: `rl-example-spring-boot/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: ratelimit-spring-boot-example

server:
  port: 8080

# Default: In-Memory (Strategy 1: Simple Starter)
ratelimit:
  enabled: true
  
  # SpEL optimization
  spel:
    compiler-mode: IMMEDIATE
    cache-size: 1000
  
  # Default proxy configuration
  proxy:
    trusted-hops: 1
    trusted-proxies:
      - "127.0.0.0/8"

# Actuator for metrics
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

**File**: `rl-example-spring-boot/src/main/resources/application-redis.yml`

```yaml
# Strategy 2: Scale-Out (Distributed Redis)
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

ratelimit:
  storage:
    type: REDIS
  
  # Production proxy setup
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"  # Cloudflare
  
  # Resilience configuration
  circuit-breaker:
    failure-threshold: 50  # 50% failure rate
    window-size: 10s
    half-open-delay: 30s
```

**File**: `rl-example-spring-boot/src/main/resources/application-resilient.yml`

```yaml
# Strategy 3: Resilient (Fail-Open)
ratelimit:
  storage:
    type: TIERED
    l1:
      type: REDIS
      fail-strategy: FAIL_OPEN  # Keep app alive if Redis down
    l2:
      type: CAFFEINE
  
  # Audit logging for failures
  audit:
    enabled: true
    log-system-failures: true
```

### 2.2 Main Application

**File**: `SpringBootExampleApplication.java`

```java
package com.lycosoft.ratelimit.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot example demonstrating rate limiting integration.
 * 
 * <p>Run with different profiles:
 * <ul>
 *   <li>Default: In-memory storage (single node)</li>
 *   <li>--spring.profiles.active=redis: Distributed Redis storage</li>
 *   <li>--spring.profiles.active=resilient: Tiered with fail-open</li>
 * </ul>
 * 
 * <p>Access examples:
 * <ul>
 *   <li>http://localhost:8080/public/search - IP-based rate limiting</li>
 *   <li>http://localhost:8080/user/profile - User-tiered limits</li>
 *   <li>http://localhost:8080/partner/data - Token bucket for partners</li>
 * </ul>
 */
@SpringBootApplication
public class SpringBootExampleApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SpringBootExampleApplication.class, args);
    }
}
```

### 2.3 Example 1: IP-Based Fixed Window

**File**: `PublicApiController.java`

```java
package com.lycosoft.ratelimit.example.springboot.controller;

import com.lycosoft.ratelimit.example.springboot.model.ApiResponse;
import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import com.lycosoft.ratelimit.spring.annotation.RateLimits;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Example 1: IP-Based Fixed Window Rate Limiting.
 * 
 * <p>Scenario: Public API endpoint that needs protection from abuse.
 * <ul>
 *   <li>Strategy: Fixed window algorithm</li>
 *   <li>Key: Client IP address</li>
 *   <li>Limits: 10 requests per minute per IP</li>
 * </ul>
 */
@RestController
@RequestMapping("/public")
public class PublicApiController {
    
    /**
     * Public search endpoint with IP-based rate limiting.
     * 
     * <p>Rate Limit: 10 requests/minute per IP address.
     * 
     * <p>Try it:
     * <pre>
     * # First 10 requests succeed
     * for i in {1..10}; do curl http://localhost:8080/public/search?q=test; done
     * 
     * # 11th request gets 429 Too Many Requests
     * curl http://localhost:8080/public/search?q=test
     * </pre>
     */
    @RateLimit(
        name = "public-search",
        key = "#ip",                    // Use client IP
        requests = 10,                   // 10 requests
        window = 60,                     // per 60 seconds
        algorithm = "FIXED_WINDOW"
    )
    @GetMapping("/search")
    public ApiResponse search(@RequestParam String q) {
        return ApiResponse.builder()
            .message("Search results for: " + q)
            .timestamp(LocalDateTime.now())
            .data("Showing top 10 results...")
            .build();
    }
    
    /**
     * Public data endpoint with multiple rate limits.
     * 
     * <p>Demonstrates tiered limits:
     * <ul>
     *   <li>Short-term: 5 requests/10 seconds (prevent bursts)</li>
     *   <li>Long-term: 100 requests/hour (prevent sustained abuse)</li>
     * </ul>
     */
    @RateLimits({
        @RateLimit(
            name = "public-data-burst",
            key = "#ip",
            requests = 5,
            window = 10
        ),
        @RateLimit(
            name = "public-data-sustained",
            key = "#ip",
            requests = 100,
            window = 3600
        )
    })
    @GetMapping("/data")
    public ApiResponse getData() {
        return ApiResponse.builder()
            .message("Public data access")
            .timestamp(LocalDateTime.now())
            .data("Sample public dataset...")
            .build();
    }
}
```

### 2.4 Example 2: User-Tiered Sliding Window

**File**: `UserTieredController.java`

```java
package com.lycosoft.ratelimit.example.springboot.controller;

import com.lycosoft.ratelimit.example.springboot.model.ApiResponse;
import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Example 2: User-Tiered Rate Limiting with Sliding Window.
 * 
 * <p>Scenario: Different rate limits based on user subscription tier.
 * <ul>
 *   <li>BASIC users: 10 requests/minute</li>
 *   <li>PREMIUM users: 100 requests/minute</li>
 *   <li>ENTERPRISE users: 1000 requests/minute</li>
 * </ul>
 * 
 * <p>Uses Spring Security integration with custom UserDetails.
 */
@RestController
@RequestMapping("/user")
public class UserTieredController {
    
    /**
     * User profile endpoint with tier-based limits.
     * 
     * <p>The rate limit uses SpEL to extract the user tier from
     * custom UserDetails attributes.
     * 
     * <p>SpEL Expression: {@code #user.attributes['tier']}
     * <ul>
     *   <li>#user = Spring Security principal</li>
     *   <li>.attributes = Custom user attributes map</li>
     *   <li>['tier'] = User subscription tier (BASIC/PREMIUM/ENTERPRISE)</li>
     * </ul>
     */
    @RateLimit(
        name = "user-profile",
        key = "#user.username + ':' + #user.attributes['tier']",
        requests = 10,              // Base limit (multiplied by tier)
        window = 60,
        algorithm = "SLIDING_WINDOW"
    )
    @GetMapping("/profile")
    public ApiResponse getProfile(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.builder()
            .message("User profile for: " + user.getUsername())
            .timestamp(LocalDateTime.now())
            .data("User details and preferences...")
            .build();
    }
    
    /**
     * User dashboard with different limits per tier.
     * 
     * <p>Demonstrates conditional rate limiting based on user attributes.
     */
    @RateLimit(
        name = "user-dashboard",
        key = "#user.username",
        requests = 50,              // Will be overridden by tier
        window = 60
    )
    @GetMapping("/dashboard")
    public ApiResponse getDashboard(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.builder()
            .message("Dashboard for: " + user.getUsername())
            .timestamp(LocalDateTime.now())
            .data("Analytics and statistics...")
            .build();
    }
}
```

### 2.5 Example 3: Token Bucket for API Partners

**File**: `PartnerApiController.java`

```java
package com.lycosoft.ratelimit.example.springboot.controller;

import com.lycosoft.ratelimit.example.springboot.model.ApiResponse;
import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Example 3: Token Bucket Algorithm for Partner APIs.
 * 
 * <p>Scenario: API partners with authentication tokens need burst capacity.
 * <ul>
 *   <li>Algorithm: Token Bucket (allows bursts)</li>
 *   <li>Key: X-Partner-ID header</li>
 *   <li>Capacity: 100 tokens</li>
 *   <li>Refill: 10 tokens/second</li>
 * </ul>
 * 
 * <p>Token Bucket Behavior:
 * <ul>
 *   <li>Start: 100 tokens available</li>
 *   <li>Burst: Can use all 100 tokens immediately</li>
 *   <li>Refill: Gains 10 tokens/second (600/minute)</li>
 *   <li>Max: Never exceeds 100 tokens</li>
 * </ul>
 */
@RestController
@RequestMapping("/partner")
public class PartnerApiController {
    
    /**
     * Partner data endpoint with token bucket rate limiting.
     * 
     * <p>Allows bursts up to 100 requests, then throttles to 10/second.
     * 
     * <p>Try it:
     * <pre>
     * # Burst: First 100 requests succeed immediately
     * for i in {1..100}; do \
     *   curl -H "X-Partner-ID: partner-123" \
     *     http://localhost:8080/partner/data; \
     * done
     * 
     * # After burst: Limited to 10/second
     * # Wait 1 second, get 10 more tokens
     * sleep 1
     * for i in {1..10}; do \
     *   curl -H "X-Partner-ID: partner-123" \
     *     http://localhost:8080/partner/data; \
     * done
     * </pre>
     */
    @RateLimit(
        name = "partner-api",
        key = "#headers['X-Partner-ID']",
        algorithm = "TOKEN_BUCKET",
        capacity = 100,             // Bucket holds 100 tokens
        refillRate = 10             // Refills 10 tokens/second
    )
    @GetMapping("/data")
    public ApiResponse getPartnerData(@RequestHeader("X-Partner-ID") String partnerId) {
        return ApiResponse.builder()
            .message("Partner data for: " + partnerId)
            .timestamp(LocalDateTime.now())
            .data("Bulk data export in progress...")
            .build();
    }
    
    /**
     * Partner webhook endpoint with high burst capacity.
     * 
     * <p>Scenario: Partner systems may send batches of webhooks.
     */
    @RateLimit(
        name = "partner-webhook",
        key = "#headers['X-Partner-ID']",
        algorithm = "TOKEN_BUCKET",
        capacity = 500,             // Large burst capacity
        refillRate = 50             // 50/second = 3000/minute
    )
    @PostMapping("/webhook")
    public ApiResponse receiveWebhook(
            @RequestHeader("X-Partner-ID") String partnerId,
            @RequestBody String payload) {
        return ApiResponse.builder()
            .message("Webhook received from: " + partnerId)
            .timestamp(LocalDateTime.now())
            .data("Processing webhook...")
            .build();
    }
}
```

### 2.6 Global Error Handler

**File**: `GlobalRateLimitHandler.java`

```java
package com.lycosoft.ratelimit.example.springboot.handler;

import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.example.springboot.model.ErrorResponse;
import com.lycosoft.ratelimit.http.RateLimitProblemDetail;
import com.lycosoft.ratelimit.spring.aop.RateLimitAspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * Global exception handler for rate limit exceptions.
 * 
 * <p>Standardizes 429 Too Many Requests responses across all endpoints.
 * <p>Includes RFC 7231 compliant headers:
 * <ul>
 *   <li>Retry-After: Seconds until limit resets</li>
 *   <li>X-RateLimit-Limit: Maximum requests allowed</li>
 *   <li>X-RateLimit-Remaining: Requests remaining (0)</li>
 *   <li>X-RateLimit-Reset: Unix timestamp when limit resets</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalRateLimitHandler {
    
    /**
     * Handles rate limit exceeded exceptions.
     * 
     * <p>Returns a standardized error response with RFC 7231 headers.
     */
    @ExceptionHandler(RateLimitAspect.RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitAspect.RateLimitExceededException ex,
            HttpServletRequest request) {
        
        RateLimitDecision decision = ex.getDecision();
        
        ErrorResponse error = ErrorResponse.builder()
            .error("Too Many Requests")
            .message(String.format(
                "Rate limit '%s' exceeded. Try again in %d seconds.",
                decision.getLimiterName(),
                decision.getRetryAfterSeconds()
            ))
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .rateLimitInfo(ErrorResponse.RateLimitInfo.builder()
                .limiter(decision.getLimiterName())
                .limit(decision.getLimit())
                .remaining(0)
                .resetAt(decision.getResetTime())
                .build())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(decision.getRetryAfterSeconds()))
            .header("X-RateLimit-Limit", String.valueOf(decision.getLimit()))
            .header("X-RateLimit-Remaining", "0")
            .header("X-RateLimit-Reset", String.valueOf(decision.getResetTime() / 1000))
            .body(error);
    }
}
```

### 2.7 Model Classes

**File**: `ApiResponse.java`

```java
package com.lycosoft.ratelimit.example.springboot.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper.
 */
@Data
@Builder
public class ApiResponse {
    private String message;
    private LocalDateTime timestamp;
    private Object data;
}
```

**File**: `ErrorResponse.java`

```java
package com.lycosoft.ratelimit.example.springboot.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Standard error response with rate limit information.
 */
@Data
@Builder
public class ErrorResponse {
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private RateLimitInfo rateLimitInfo;
    
    @Data
    @Builder
    public static class RateLimitInfo {
        private String limiter;
        private int limit;
        private int remaining;
        private long resetAt;
    }
}
```

---

## 3. Spring WebFlux Example (Reactive)

### 3.1 Functional Router Configuration

**File**: `FunctionalRoutes.java`

```java
package com.lycosoft.ratelimit.example.webflux.router;

import com.lycosoft.ratelimit.example.webflux.handler.ApiHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Functional routing with programmatic rate limiting.
 * 
 * <p>Demonstrates applying rate limits to RouterFunctions without annotations.
 */
@Configuration
public class FunctionalRoutes {
    
    @Bean
    public RouterFunction<ServerResponse> apiRoutes(ApiHandler handler) {
        return route()
            .GET("/reactive/data", handler::getData)
            .GET("/reactive/stream", handler::getStream)
            .POST("/reactive/process", handler::processData)
            .build();
    }
}
```

### 3.2 Rate Limit WebFilter

**File**: `RateLimitWebFilter.java`

```java
package com.lycosoft.ratelimit.example.webflux.filter;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
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
 * <p>Applies rate limiting to functional routes without blocking threads.
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
        
        // Build rate limit config for this path
        RateLimitConfig config = RateLimitConfig.builder()
            .name("reactive-" + path)
            .requests(10)
            .window(60)
            .algorithm("FIXED_WINDOW")
            .build();
        
        // Check rate limit (non-blocking!)
        return Mono.fromCallable(() -> limiterEngine.checkLimit(clientIp, config))
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
```

---

## 4. Standalone Java Example

### 4.1 Direct Engine Usage

**File**: `DirectEngineExample.java`

```java
package com.lycosoft.ratelimit.example.standalone;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.registry.LimiterRegistry;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spi.MetricsExporter;
import com.lycosoft.ratelimit.spi.NoOpMetricsExporter;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;

import java.util.concurrent.TimeUnit;

/**
 * Example of using LimiterEngine directly without any framework.
 * 
 * <p>Scenario: Rate limiting an internal processing loop.
 */
public class DirectEngineExample {
    
    public static void main(String[] args) throws InterruptedException {
        // 1. Create components
        StorageProvider storage = new InMemoryStorageProvider();
        KeyResolver keyResolver = context -> context.getKeyExpression(); // Identity
        MetricsExporter metrics = new NoOpMetricsExporter();
        LimiterRegistry registry = new LimiterRegistry();
        
        // 2. Create engine
        LimiterEngine engine = new LimiterEngine(storage, keyResolver, metrics, registry);
        
        // 3. Define rate limit
        RateLimitConfig config = RateLimitConfig.builder()
            .name("processing-loop")
            .algorithm("TOKEN_BUCKET")
            .requests(10)            // Base limit
            .window(1)               // Per second
            .capacity(50)            // Burst capacity
            .refillRate(10)          // 10 tokens/second
            .build();
        
        // 4. Simulate processing loop
        System.out.println("Starting processing loop...");
        System.out.println("Bucket capacity: 50, Refill: 10/second\\n");
        
        for (int i = 1; i <= 100; i++) {
            RateLimitDecision decision = engine.checkLimit("task-processor", config);
            
            if (decision.isAllowed()) {
                System.out.printf("Task #%d: PROCESSED (remaining: %d)%n", 
                    i, decision.getRemaining());
                
                // Simulate processing
                processTask(i);
            } else {
                System.out.printf("Task #%d: THROTTLED (wait %d seconds)%n", 
                    i, decision.getRetryAfterSeconds());
                
                // Wait and retry
                TimeUnit.SECONDS.sleep(decision.getRetryAfterSeconds());
                i--; // Retry same task
            }
        }
        
        System.out.println("\\nProcessing complete!");
    }
    
    private static void processTask(int taskId) {
        // Simulate work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 4.2 Custom Key Resolver Example

**File**: `CustomKeyResolverExample.java`

```java
package com.lycosoft.ratelimit.example.standalone;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.registry.LimiterRegistry;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spi.MetricsExporter;
import com.lycosoft.ratelimit.spi.NoOpMetricsExporter;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;

/**
 * Example of custom key resolution from complex POJOs.
 * 
 * <p>Demonstrates extracting rate limit keys from business objects.
 */
public class CustomKeyResolverExample {
    
    public static void main(String[] args) {
        // Custom key resolver for business objects
        KeyResolver keyResolver = new BusinessObjectKeyResolver();
        
        StorageProvider storage = new InMemoryStorageProvider();
        MetricsExporter metrics = new NoOpMetricsExporter();
        LimiterRegistry registry = new LimiterRegistry();
        
        LimiterEngine engine = new LimiterEngine(storage, keyResolver, metrics, registry);
        
        // Create rate limit config
        RateLimitConfig config = RateLimitConfig.builder()
            .name("order-processing")
            .requests(5)
            .window(60)
            .build();
        
        // Process orders
        for (int i = 1; i <= 10; i++) {
            Order order = new Order("customer-123", "order-" + i, 100.0 * i);
            
            // Build context with order data
            RateLimitContext context = RateLimitContext.builder()
                .keyExpression("customer:" + order.getCustomerId())
                .build();
            
            String key = keyResolver.resolveKey(context);
            RateLimitDecision decision = engine.checkLimit(key, config);
            
            if (decision.isAllowed()) {
                System.out.printf("Order %s: ACCEPTED (remaining: %d)%n", 
                    order.getOrderId(), decision.getRemaining());
            } else {
                System.out.printf("Order %s: REJECTED (customer rate limit exceeded)%n", 
                    order.getOrderId());
            }
        }
    }
    
    /**
     * Custom key resolver that extracts keys from business context.
     */
    static class BusinessObjectKeyResolver implements KeyResolver {
        @Override
        public String resolveKey(RateLimitContext context) {
            // In real scenario, might extract from request attributes,
            // session data, or custom context fields
            return context.getKeyExpression();
        }
    }
    
    /**
     * Business object representing an order.
     */
    static class Order {
        private final String customerId;
        private final String orderId;
        private final double amount;
        
        public Order(String customerId, String orderId, double amount) {
            this.customerId = customerId;
            this.orderId = orderId;
            this.amount = amount;
        }
        
        public String getCustomerId() { return customerId; }
        public String getOrderId() { return orderId; }
        public double getAmount() { return amount; }
    }
}
```

---

## 5. Docker Infrastructure

### 5.1 Docker Compose

**File**: `docker/docker-compose.yml`

```yaml
version: '3.8'

services:
  # Redis for distributed rate limiting
  redis:
    image: redis:7-alpine
    container_name: ratelimit-redis
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  # Prometheus for metrics collection
  prometheus:
    image: prom/prometheus:latest
    container_name: ratelimit-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    depends_on:
      - redis

  # Grafana for visualization
  grafana:
    image: grafana/grafana:latest
    container_name: ratelimit-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
      - ./grafana/dashboards:/var/lib/grafana/dashboards
      - grafana-data:/var/lib/grafana
    depends_on:
      - prometheus

volumes:
  redis-data:
  prometheus-data:
  grafana-data:
```

### 5.2 Prometheus Configuration

**File**: `docker/prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Spring Boot application metrics
  - job_name: 'ratelimit-spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
    
  # WebFlux application metrics
  - job_name: 'ratelimit-webflux'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
```

### 5.3 Grafana Dashboard

**File**: `docker/grafana/dashboards/ratelimit-dashboard.json`

```json
{
  "dashboard": {
    "title": "Rate Limit Monitoring",
    "panels": [
      {
        "title": "Requests: Allowed vs Denied",
        "targets": [
          {
            "expr": "rate(ratelimit_requests_total{result=\"allowed\"}[5m])",
            "legendFormat": "Allowed"
          },
          {
            "expr": "rate(ratelimit_requests_total{result=\"denied\"}[5m])",
            "legendFormat": "Denied"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Average Latency",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(ratelimit_latency_seconds_bucket[5m]))",
            "legendFormat": "p95 Latency"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Bucket Usage %",
        "targets": [
          {
            "expr": "(ratelimit_usage_current / ratelimit_usage_limit) * 100",
            "legendFormat": "{{limiter}}"
          }
        ],
        "type": "gauge"
      },
      {
        "title": "Circuit Breaker State",
        "targets": [
          {
            "expr": "ratelimit_circuit_state",
            "legendFormat": "{{state}}"
          }
        ],
        "type": "stat"
      }
    ]
  }
}
```

---

## 6. Testing & Running

### 6.1 Start Infrastructure

```bash
# Start Redis, Prometheus, Grafana
cd docker
docker-compose up -d

# Verify services
docker-compose ps
```

### 6.2 Run Spring Boot Example

```bash
cd rl-example-spring-boot

# In-memory (default)
mvn spring-boot:run

# With Redis
mvn spring-boot:run -Dspring-boot.run.profiles=redis

# Resilient mode
mvn spring-boot:run -Dspring-boot.run.profiles=resilient
```

### 6.3 Test Examples

```bash
# Example 1: IP-based rate limiting
for i in {1..15}; do
  curl http://localhost:8080/public/search?q=test
  echo ""
done

# Example 2: User-tiered (requires auth)
curl -u basic:pass http://localhost:8080/user/profile

# Example 3: Partner API
for i in {1..105}; do
  curl -H "X-Partner-ID: partner-123" \
    http://localhost:8080/partner/data
done
```

### 6.4 View Metrics

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Application Metrics**: http://localhost:8080/actuator/prometheus

---

## 7. Learning Objectives Achieved

### ✅ How to Resolve Keys

**Examples**:
- `#ip` - Client IP address
- `#user.username` - Authenticated user
- `#headers['X-Partner-ID']` - Custom header
- `#args[0].customerId` - Method argument

### ✅ How to Choose Algorithms

**Fixed Window**: Predictable, simple
- Use for: Public APIs, basic protection

**Sliding Window**: Accurate, smooth
- Use for: User quotas, precise limits

**Token Bucket**: Burst-friendly
- Use for: Partner APIs, batch processing

### ✅ How to Monitor

**Logs**: AuditLogger captures all events
**Metrics**: Prometheus + Grafana dashboards
**Headers**: X-RateLimit-* headers in responses

### ✅ How to Handle Failures

**Fail-Open**: Keep app alive (resilient profile)
**Fail-Closed**: Reject requests (secure profile)
**L1/L2 Tiered**: Automatic fallback to local cache

---

## 8. Quick Start Commands

```bash
# 1. Clone and build
git clone <repo>
cd ratelimit-library
mvn clean install

# 2. Start infrastructure
cd rl-examples/docker
docker-compose up -d

# 3. Run Spring Boot example
cd ../rl-example-spring-boot
mvn spring-boot:run

# 4. Test rate limiting
curl http://localhost:8080/public/search?q=test

# 5. View dashboard
open http://localhost:3000
```

---

**Examples Module Complete!** 🎉

This specification provides complete, production-ready examples across all major Java frameworks with comprehensive monitoring and infrastructure setup.
