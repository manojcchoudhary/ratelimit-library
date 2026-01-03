# Spring Boot Implementation Guide

A comprehensive guide for implementing rate limiting in Spring Boot applications using annotations and auto-configuration.

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Annotation API](#annotation-api)
4. [YAML Configuration](#yaml-configuration)
5. [SpEL Key Expressions](#spel-key-expressions)
6. [Storage Configuration](#storage-configuration)
7. [Advanced Features](#advanced-features)
8. [Security Features](#security-features)
9. [Monitoring & Observability](#monitoring--observability)
10. [Production Deployment](#production-deployment)
11. [Examples](#examples)
12. [Troubleshooting](#troubleshooting)

---

## Overview

The Spring Boot adapter provides seamless integration with annotation-driven rate limiting, auto-configuration, and full YAML support.

### Features

- **Zero Configuration**: Works out of the box with sensible defaults
- **Annotation-Driven**: `@RateLimit` and `@RateLimits` annotations
- **SpEL Support**: Dynamic key expressions with compiled bytecode
- **Auto-Configuration**: Automatic bean setup based on dependencies
- **Full YAML Support**: Complete configuration via `application.yml`

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                        │
├─────────────────────────────────────────────────────────────────┤
│  @RateLimit  │  @RateLimits                                     │
│  (Annotations)                                                   │
├─────────────────────────────────────────────────────────────────┤
│  RateLimitAspect (AOP)                                          │
│  ├─ SpEL Key Resolution                                          │
│  ├─ IP Resolution (Hop Counting)                                 │
│  └─ Adaptive Throttling                                          │
├─────────────────────────────────────────────────────────────────┤
│  RateLimitAutoConfiguration                                      │
│  ├─ StorageProvider (Auto-detected)                              │
│  ├─ MetricsExporter (Micrometer)                                 │
│  └─ Properties Binding                                           │
├─────────────────────────────────────────────────────────────────┤
│  Storage: Caffeine (default) │ Redis │ Tiered                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Getting Started

### 1. Add Dependency

**Maven:**

```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-adapter-spring</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>

<!-- For Redis storage (optional) -->
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-spi-redis</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'com.lycosoft:rl-adapter-spring:0.1.0-beta.1'
implementation 'com.lycosoft:rl-spi-redis:0.1.0-beta.1'  // Optional
```

### 2. Apply Annotation

```java
@RestController
@RequestMapping("/api")
public class OrderController {

    @RateLimit(requests = 100, window = 60)
    @GetMapping("/orders")
    public List<Order> getOrders() {
        return orderService.findAll();
    }
}
```

### 3. Configure (Optional)

```yaml
# application.yml
ratelimit:
  enabled: true
```

That's it! The library auto-configures itself with:
- In-memory storage (Caffeine)
- SpEL compilation enabled
- Default key: `#ip` (client IP address)

---

## Annotation API

### @RateLimit

The primary annotation for rate limiting methods.

```java
@RateLimit(
    name = "api-endpoint",                           // Optional name
    key = "#user.id",                                // SpEL key expression
    requests = 100,                                  // Max requests
    window = 60,                                     // Window duration
    windowUnit = TimeUnit.SECONDS,                   // Time unit
    algorithm = RateLimitConfig.Algorithm.TOKEN_BUCKET,  // Algorithm
    failStrategy = RateLimitConfig.FailStrategy.FAIL_OPEN,  // Failure behavior
    capacity = 150,                                  // Token bucket capacity
    refillRate = 1.67                                // Tokens per second
)
```

#### Annotation Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | String | Method name | Unique limiter identifier |
| `key` | String | `"#ip"` | SpEL expression for rate limit key |
| `requests` | int | Required | Maximum requests in window |
| `window` | long | Required | Window duration |
| `windowUnit` | TimeUnit | `SECONDS` | Time unit for window |
| `algorithm` | Algorithm | `TOKEN_BUCKET` | Rate limiting algorithm |
| `failStrategy` | FailStrategy | `FAIL_OPEN` | Behavior when storage fails |
| `capacity` | int | `requests` | Token bucket capacity |
| `refillRate` | double | Auto | Tokens per second |

### @RateLimits (Tiered Limiting)

Apply multiple rate limits to a single endpoint.

```java
@RateLimits({
    @RateLimit(name = "burst", requests = 10, window = 1),
    @RateLimit(name = "minute", requests = 100, window = 60),
    @RateLimit(name = "hourly", requests = 1000, window = 3600)
})
@GetMapping("/api/resource")
public Resource getResource() {
    return resourceService.fetch();
}
```

### Annotation Placement

**Method Level:**

```java
@RestController
public class ApiController {

    @RateLimit(requests = 100, window = 60)
    @GetMapping("/data")
    public Data getData() {
        return service.fetchData();
    }
}
```

**Class Level (Default for all methods):**

```java
@RateLimit(requests = 50, window = 60)
@RestController
public class ApiController {

    @GetMapping("/data")  // Inherits class-level limit
    public Data getData() { ... }

    @RateLimit(requests = 100, window = 60)  // Override
    @GetMapping("/other")
    public Other getOther() { ... }
}
```

---

## YAML Configuration

### Complete Reference

```yaml
ratelimit:
  # Master enable/disable switch
  enabled: true

  # SpEL Expression Configuration
  spel:
    # Compiler mode for 40× performance boost
    # IMMEDIATE: Compile on first access (recommended)
    # MIXED: Compile after several uses
    # OFF: Never compile (debugging)
    compiler-mode: IMMEDIATE

    # Expression cache size
    cache-size: 1000

  # Proxy Configuration (IP Resolution)
  proxy:
    # Number of trusted hops from right in X-Forwarded-For
    # 0: Use rightmost IP
    # 1: Single proxy (Nginx)
    # 2: CDN + proxy (Cloudflare + Nginx)
    # 3: CDN + ALB + proxy
    trusted-hops: 1

    # CIDR ranges of trusted proxies
    trusted-proxies:
      - "127.0.0.0/8"         # Localhost IPv4
      - "::1/128"             # Localhost IPv6
      - "10.0.0.0/8"          # Private Class A
      - "172.16.0.0/12"       # Private Class B (Docker/K8s)
      - "192.168.0.0/16"      # Private Class C
      # CDN IPs (examples):
      # - "104.16.0.0/12"     # Cloudflare
      # - "35.156.0.0/14"     # AWS ALB

  # Adaptive Throttling
  throttling:
    # Enable gradual slowdown
    enabled: false

    # Percentage where throttling begins (1-99)
    soft-limit: 80

    # Maximum delay at hard limit
    max-delay-ms: 2000

    # Strategy: LINEAR or EXPONENTIAL
    strategy: LINEAR

  # Alternative throttle config (same as throttling)
  throttle:
    enabled: false
    soft-limit-percentage: 80
    max-delay-ms: 2000
    strategy: LINEAR
    non-blocking: false

  # RFC 9457 Problem Details
  problem-details:
    # Enable standardized error responses
    enabled: true

    # Include extension fields (limit, remaining, reset)
    include-extensions: true

    # Custom problem type URI
    type-uri: "https://api.example.com/probs/rate-limit"
```

### Environment-Specific Configuration

**application-dev.yml:**

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: OFF       # Better debugging
    cache-size: 100
  proxy:
    trusted-hops: 0          # Direct connection
    trusted-proxies: []
  throttling:
    enabled: false
```

**application-staging.yml:**

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: MIXED
    cache-size: 500
  proxy:
    trusted-hops: 1
    trusted-proxies:
      - "10.0.0.0/8"
  throttling:
    enabled: true
    soft-limit: 90
    max-delay-ms: 1000
```

**application-prod.yml:**

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE
    cache-size: 2000
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"
  throttling:
    enabled: true
    soft-limit: 80
    max-delay-ms: 2000
    strategy: LINEAR
  problem-details:
    enabled: true
    include-extensions: true
```

---

## SpEL Key Expressions

### Available Variables

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `#ip` | Client IP address | `"192.168.1.100"` |
| `#user` | Authenticated principal | `User{id=123}` |
| `#args` | Method arguments array | `[request, param1]` |
| `#args[0]` | First argument | `OrderRequest` |
| `#headers` | HTTP headers map | `{Authorization=...}` |
| `#request` | First @RequestBody argument | Object fields |
| `#methodName` | Current method name | `"getOrders"` |

### Common Patterns

**Per-IP Limiting:**

```java
@RateLimit(key = "#ip", requests = 1000, window = 3600)
@GetMapping("/public")
public Data getPublicData() { ... }
```

**Per-User Limiting:**

```java
@RateLimit(key = "#user.id", requests = 100, window = 60)
@PostMapping("/orders")
public Order createOrder(@AuthenticationPrincipal User user) { ... }
```

**Per-Tenant Limiting:**

```java
@RateLimit(key = "#headers['X-Tenant-ID']", requests = 5000, window = 3600)
@GetMapping("/tenant/data")
public TenantData getTenantData() { ... }
```

**Composite Keys:**

```java
// Tenant + User
@RateLimit(key = "#tenant.id + ':' + #user.id", requests = 50, window = 60)

// API Key + Endpoint
@RateLimit(key = "#headers['X-API-Key'] + ':' + #methodName", requests = 100, window = 60)

// Region + User
@RateLimit(key = "#headers['X-Region'] + ':' + #user.id", requests = 200, window = 60)
```

**Request Body Fields:**

```java
@RateLimit(key = "#request.customerId", requests = 20, window = 60)
@PostMapping("/process")
public Result process(@RequestBody ProcessRequest request) { ... }
```

**Conditional Keys:**

```java
// Different limits for authenticated vs anonymous
@RateLimit(
    key = "#user != null ? 'auth:' + #user.id : 'anon:' + #ip",
    requests = 100,
    window = 60
)
@GetMapping("/data")
public Data getData(@AuthenticationPrincipal User user) { ... }
```

**Path Variables:**

```java
@RateLimit(key = "'resource:' + #args[0]", requests = 50, window = 60)
@GetMapping("/resource/{resourceId}")
public Resource getResource(@PathVariable String resourceId) { ... }
```

---

## Storage Configuration

### In-Memory (Default)

Uses Caffeine cache - no additional configuration needed.

```yaml
# No configuration required - uses Caffeine by default
```

**Custom Bean Override:**

```java
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public StorageProvider storageProvider() {
        return new CaffeineStorageProvider(
            10_000,                    // maxEntries
            Duration.ofHours(2)        // ttl
        );
    }
}
```

### Redis Storage

**1. Add Dependency:**

```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-spi-redis</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

**2. Configure Redis:**

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
    timeout: 2000
    jedis:
      pool:
        max-active: 50
        max-idle: 10
        min-idle: 5
```

**3. Create Bean:**

```java
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public StorageProvider storageProvider(JedisPool jedisPool) {
        return new RedisStorageProvider(jedisPool);
    }

    @Bean
    public JedisPool jedisPool(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port}") int port,
            @Value("${spring.redis.password:}") String password) {

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(10);
        config.setMinIdle(5);
        config.setTestOnBorrow(true);

        if (password != null && !password.isEmpty()) {
            return new JedisPool(config, host, port, 2000, password);
        }
        return new JedisPool(config, host, port);
    }
}
```

### Tiered Storage (Redis + Caffeine)

Best for production deployments with failover.

```java
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public StorageProvider storageProvider(JedisPool jedisPool) {
        // L1: Redis (distributed, primary)
        RedisStorageProvider l1 = new RedisStorageProvider(jedisPool);

        // L2: Caffeine (local, fallback)
        CaffeineStorageProvider l2 = new CaffeineStorageProvider();

        // Tiered with fail-open strategy
        return new TieredStorageProvider(
            l1,
            l2,
            RateLimitConfig.FailStrategy.FAIL_OPEN
        );
    }
}
```

---

## Advanced Features

### Adaptive Throttling

Gradual slowdown instead of hard blocking.

```yaml
ratelimit:
  throttling:
    enabled: true
    soft-limit: 80           # Start at 80%
    max-delay-ms: 2000       # Max 2 second delay
    strategy: LINEAR         # or EXPONENTIAL
```

**Behavior:**

```
Usage:     0%  ─────── 80% ──────── 100% ─── 100%+
Response:  OK     OK (slow)      OK (2s)   429 Error
Delay:     0ms    0-2000ms       2000ms    Rejected
```

**Strategy Comparison:**

| Usage | LINEAR | EXPONENTIAL |
|-------|--------|-------------|
| 80% | 0ms | 0ms |
| 85% | 500ms | 125ms |
| 90% | 1000ms | 400ms |
| 95% | 1500ms | 900ms |
| 100% | 2000ms | 2000ms |

### RFC 9457 Problem Details

Standardized machine-readable error responses.

```yaml
ratelimit:
  problem-details:
    enabled: true
    include-extensions: true
```

**Response:**

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 24

{
  "type": "https://ratelimit.io/probs/too-many-requests",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit 'api-orders' exceeded. Retry in 24 seconds.",
  "instance": "/api/orders",
  "retry_after": 24,
  "limit": 100,
  "remaining": 0,
  "reset": 1703635200
}
```

### Hop Counting (IP Spoofing Prevention)

```yaml
ratelimit:
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"
```

**How It Works:**

```
Client → Cloudflare → Nginx → App

X-Forwarded-For: client_ip, cloudflare_ip
Remote Address: nginx_ip

With trusted-hops=2:
  1. Verify nginx_ip is trusted ✓
  2. Count 2 hops from right
  3. Result: client_ip (correct!)

Without hop counting:
  Result: First IP (could be spoofed!)
```

### Custom Exception Handler

```java
@ControllerAdvice
public class RateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatus(
            HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too Many Requests");
        problem.setDetail("Rate limit exceeded. Retry in " +
            ex.getRetryAfterSeconds() + " seconds.");
        problem.setProperty("retry_after", ex.getRetryAfterSeconds());
        problem.setProperty("limit", ex.getLimit());
        problem.setProperty("remaining", 0);

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(problem);
    }
}
```

---

## Security Features

### SpEL Injection Prevention

The library blocks dangerous SpEL patterns automatically.

**Blocked Keywords:**

```
class, classloader, forname, runtime, system, process,
exec, method, constructor, field, invoke, reflect,
securitymanager, file, socket, scriptengine, getenv
```

**Safe Usage:**

```java
// Safe - accessing user properties
@RateLimit(key = "#user.id")

// Safe - accessing headers
@RateLimit(key = "#headers['X-API-Key']")

// BLOCKED - dangerous patterns
@RateLimit(key = "T(java.lang.Runtime).getRuntime()")  // Blocked!
```

### Sensitive Data Filtering

Automatic masking in audit logs.

**Masked Patterns:**

```
password, passwd, pwd, secret, token, apikey, api_key,
api-key, auth, credential, private, bearer
```

### IP Resolution Security

```yaml
ratelimit:
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "10.0.0.0/8"           # Only trust internal network
      - "104.16.0.0/12"        # And Cloudflare
```

---

## Monitoring & Observability

### Prometheus Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  metrics:
    export:
      prometheus:
        enabled: true
```

**Available Metrics:**

```prometheus
# Request counters
ratelimit_requests_total{result="allowed", limiter="api-orders"}
ratelimit_requests_total{result="denied", limiter="api-orders"}

# Latency histogram
ratelimit_latency_seconds_bucket{limiter="api-orders", le="0.001"}
ratelimit_latency_seconds_bucket{limiter="api-orders", le="0.01"}

# Circuit breaker
ratelimit_circuit_state{state="closed"}
ratelimit_circuit_state{state="open"}

# Throttle delay
ratelimit_throttle_delay_seconds_bucket{le="0.5"}
ratelimit_throttle_delay_seconds_bucket{le="1.0"}
ratelimit_throttle_delay_seconds_bucket{le="2.0"}
```

**Grafana Queries:**

```promql
# Denial rate
rate(ratelimit_requests_total{result="denied"}[5m])

# P95 latency
histogram_quantile(0.95, rate(ratelimit_latency_seconds_bucket[5m]))

# Success rate
sum(rate(ratelimit_requests_total{result="allowed"}[5m])) /
sum(rate(ratelimit_requests_total[5m]))
```

### Health Endpoint

```yaml
management:
  endpoint:
    health:
      show-details: always
```

```java
@Component
public class RateLimitHealthIndicator implements HealthIndicator {

    private final StorageProvider storageProvider;

    @Override
    public Health health() {
        if (storageProvider.isHealthy()) {
            return Health.up()
                .withDetails(storageProvider.getDiagnostics())
                .build();
        }
        return Health.down()
            .withDetail("error", "Storage unavailable")
            .build();
    }
}
```

### Actuator Endpoint

```java
@Component
@Endpoint(id = "ratelimit")
public class RateLimitEndpoint {

    private final StorageProvider storageProvider;

    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("healthy", storageProvider.isHealthy());
        info.put("diagnostics", storageProvider.getDiagnostics());
        return info;
    }
}
```

---

## Production Deployment

### Small (1-5 nodes)

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE
  proxy:
    trusted-hops: 1
    trusted-proxies:
      - "10.0.0.0/8"
  throttling:
    enabled: false

# Storage: Default Caffeine (in-memory)
```

### Medium (5-50 nodes)

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE
    cache-size: 1000
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"
  throttling:
    enabled: true
    soft-limit: 80
    max-delay-ms: 2000

spring:
  redis:
    host: redis.internal
    port: 6379

# Storage: Redis (configure bean)
```

### Large (50+ nodes)

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE
    cache-size: 2000
  proxy:
    trusted-hops: 3
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"
      - "35.156.0.0/14"
  throttling:
    enabled: true
    soft-limit: 85
    max-delay-ms: 3000
    strategy: EXPONENTIAL
  problem-details:
    enabled: true

spring:
  redis:
    cluster:
      nodes: redis-1:6379,redis-2:6379,redis-3:6379

# Storage: Tiered (Redis L1 + Caffeine L2)
```

### Security Checklist

- [ ] Configure correct `trusted-hops`
- [ ] Add all proxy CIDRs to `trusted-proxies`
- [ ] Enable SpEL compilation (`IMMEDIATE`)
- [ ] Configure appropriate rate limits
- [ ] Enable metrics export
- [ ] Set up alerting on denial rates
- [ ] Test failover behavior
- [ ] Review sensitive data filtering

---

## Examples

### Public API Controller

```java
@RestController
@RequestMapping("/api/public")
public class PublicApiController {

    @RateLimit(
        key = "#ip",
        requests = 100,
        window = 60,
        algorithm = Algorithm.TOKEN_BUCKET,
        capacity = 150  // Allow 50% burst
    )
    @GetMapping("/search")
    public SearchResults search(@RequestParam String query) {
        return searchService.search(query);
    }
}
```

### Authenticated API Controller

```java
@RestController
@RequestMapping("/api/v1")
public class UserApiController {

    @RateLimits({
        @RateLimit(name = "burst", key = "#user.id", requests = 10, window = 1),
        @RateLimit(name = "minute", key = "#user.id", requests = 100, window = 60),
        @RateLimit(name = "daily", key = "#user.id", requests = 10000, window = 86400)
    })
    @GetMapping("/orders")
    public List<Order> getOrders(@AuthenticationPrincipal User user) {
        return orderService.findByUser(user.getId());
    }

    @RateLimit(
        key = "#user.id",
        requests = 10,
        window = 60,
        algorithm = Algorithm.SLIDING_WINDOW,
        failStrategy = FailStrategy.FAIL_CLOSED
    )
    @PostMapping("/orders")
    public Order createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody OrderRequest request) {
        return orderService.create(user, request);
    }
}
```

### Multi-Tenant Controller

```java
@RestController
@RequestMapping("/api/tenant")
public class TenantApiController {

    @RateLimits({
        @RateLimit(
            name = "tenant-limit",
            key = "#headers['X-Tenant-ID']",
            requests = 10000,
            window = 3600
        ),
        @RateLimit(
            name = "user-limit",
            key = "#headers['X-Tenant-ID'] + ':' + #user.id",
            requests = 100,
            window = 60
        )
    })
    @GetMapping("/data")
    public TenantData getData(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @AuthenticationPrincipal User user) {
        return tenantService.getData(tenantId, user);
    }
}
```

### Webhook Controller

```java
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    @RateLimit(
        key = "#request.callbackUrl",
        requests = 100,
        window = 60,
        algorithm = Algorithm.SLIDING_WINDOW
    )
    @PostMapping("/register")
    public WebhookRegistration register(@RequestBody WebhookRequest request) {
        return webhookService.register(request);
    }
}
```

---

## Troubleshooting

### Rate Limits Not Applied

**1. Check AOP Proxy:**

```java
// Wrong: Self-invocation bypasses proxy
public void methodA() {
    methodB();  // Rate limit NOT applied!
}

@RateLimit(requests = 10, window = 60)
public void methodB() { ... }

// Right: Inject self and use proxy
@Autowired
private MyService self;

public void methodA() {
    self.methodB();  // Rate limit applied
}
```

**2. Check Method Visibility:**

```java
// Wrong: Private methods can't be proxied
@RateLimit(requests = 10, window = 60)
private void doSomething() { ... }  // NOT applied!

// Right: Public methods
@RateLimit(requests = 10, window = 60)
public void doSomething() { ... }
```

**3. Verify Configuration:**

```yaml
ratelimit:
  enabled: true  # Must be true
```

### SpEL Expression Errors

```java
// Wrong: Missing hash
@RateLimit(key = "user.id")  // Error!

// Right: Include hash
@RateLimit(key = "#user.id")

// Wrong: Invalid property
@RateLimit(key = "#user.nonExistent")  // NPE!

// Right: Null-safe navigation
@RateLimit(key = "#user?.id ?: #ip")
```

### IP Resolution Issues

```bash
# Debug IP resolution
curl -H "X-Forwarded-For: test-ip" http://localhost:8080/api/test

# Check application logs for resolved IP
```

### Storage Connection Issues

```java
@Component
public class StorageDiagnostics {

    @Autowired
    private StorageProvider storage;

    @Scheduled(fixedRate = 60000)
    public void checkHealth() {
        boolean healthy = storage.isHealthy();
        Map<String, Object> diag = storage.getDiagnostics();
        log.info("Storage health: {} - {}", healthy, diag);
    }
}
```

### Debug Logging

```yaml
logging:
  level:
    com.lycosoft.ratelimit: DEBUG
```

---

## API Reference

### Properties Classes

```java
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
    private boolean enabled = true;
    private SpelConfig spel = new SpelConfig();
    private ProxyConfig proxy = new ProxyConfig();
    private ThrottlingConfig throttling = new ThrottlingConfig();
}

public static class SpelConfig {
    private SpelCompilerMode compilerMode = SpelCompilerMode.IMMEDIATE;
    private int cacheSize = 1000;
}

public static class ProxyConfig {
    private int trustedHops = 1;
    private Set<String> trustedProxies = Set.of("127.0.0.0/8", "::1/128");
}

public static class ThrottlingConfig {
    private boolean enabled = false;
    private int softLimit = 80;
    private long maxDelayMs = 2000;
    private String strategy = "LINEAR";
}
```

### Auto-Configuration

```java
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(
    prefix = "ratelimit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RateLimitAutoConfiguration {
    // Auto-configured beans
}
```

---

**Documentation Version:** 0.1.0-beta.1
