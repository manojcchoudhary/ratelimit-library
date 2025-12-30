# Developer Guide: Rate Limiting Library

A comprehensive guide for implementing all features and configuring the Rate Limiting Library.

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Rate Limiting Algorithms](#rate-limiting-algorithms)
4. [Annotation API](#annotation-api)
5. [YAML Configuration Reference](#yaml-configuration-reference)
6. [Storage Backends](#storage-backends)
7. [Key Resolution & SpEL](#key-resolution--spel)
8. [Security Features](#security-features)
9. [Resilience Patterns](#resilience-patterns)
10. [Advanced Features](#advanced-features)
11. [Monitoring & Observability](#monitoring--observability)
12. [Framework Integration](#framework-integration)
13. [Best Practices](#best-practices)

---

## Overview

The Rate Limiting Library is a production-grade, high-performance rate limiting solution for Java applications. It provides:

- **Multiple Algorithms**: Token Bucket and Sliding Window
- **Zero Dependencies Core**: Framework-agnostic core module
- **Framework Integration**: Spring Boot and Quarkus adapters
- **Distributed Support**: Redis with atomic Lua scripts
- **High Performance**: O(1) algorithms, <500μs local overhead

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application                               │
├─────────────────────────────────────────────────────────────────┤
│  rl-adapter-spring  │  rl-adapter-quarkus                       │
│  (AOP + Annotations)│  (CDI Interceptors)                        │
├─────────────────────┴───────────────────────────────────────────┤
│                         rl-core                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐│
│  │  Algorithms │ │  Engine     │ │  Security   │ │ Resilience ││
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘│
├─────────────────────────────────────────────────────────────────┤
│           Storage Providers (SPI)                                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────────┐│
│  │  Caffeine   │ │   Redis     │ │  Tiered (L1/L2 Failover)   ││
│  │  (In-Memory)│ │ (Distributed)│ │                            ││
│  └─────────────┘ └─────────────┘ └─────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Module Overview

| Module | Description | Dependencies |
|--------|-------------|--------------|
| `rl-core` | Core algorithms, SPIs, security | Zero (framework-agnostic) |
| `rl-spi-caffeine` | In-memory storage | Caffeine |
| `rl-spi-redis` | Distributed storage | Jedis |
| `rl-adapter-spring` | Spring Boot integration | Spring Framework |
| `rl-adapter-quarkus` | Quarkus integration | Quarkus CDI |

---

## Getting Started

### Spring Boot

**1. Add Dependency:**

```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-adapter-spring</artifactId>
    <version>1.1.0</version>
</dependency>
```

**2. Apply Rate Limiting:**

```java
@RestController
public class OrderController {

    @RateLimit(requests = 100, window = 60)
    @GetMapping("/orders")
    public List<Order> getOrders() {
        return orderService.findAll();
    }
}
```

**3. Configure (Optional):**

```yaml
# application.yml
ratelimit:
  enabled: true
```

### Quarkus

**1. Add Dependency:**

```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-adapter-quarkus</artifactId>
    <version>1.1.0</version>
</dependency>
```

**2. Apply Rate Limiting:**

```java
@Path("/orders")
public class OrderResource {

    @RateLimit(requests = 100, window = 60)
    @GET
    public List<Order> getOrders() {
        return orderService.findAll();
    }
}
```

### Standalone Usage (No Framework)

```java
// Create storage provider
StorageProvider storage = new CaffeineStorageProvider();

// Create key resolver
KeyResolver keyResolver = context -> context.getKeyExpression();

// Create metrics exporter (optional)
MetricsExporter metrics = new NoOpMetricsExporter();

// Create engine
LimiterEngine engine = new LimiterEngine(storage, keyResolver, metrics, null);

// Configure rate limit
RateLimitConfig config = RateLimitConfig.builder()
    .name("my-limiter")
    .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
    .requests(100)
    .window(60)
    .windowUnit(TimeUnit.SECONDS)
    .build();

// Check rate limit
RateLimitContext context = RateLimitContext.builder()
    .keyExpression("user:123")
    .build();

RateLimitDecision decision = engine.tryAcquire(context, config);
if (decision.isAllowed()) {
    // Process request
} else {
    // Handle rate limit exceeded
    long retryAfter = decision.getRetryAfterSeconds();
}
```

---

## Rate Limiting Algorithms

### Token Bucket Algorithm

The Token Bucket algorithm is ideal for APIs that need to handle traffic bursts while maintaining an average rate.

**Characteristics:**
- Allows smooth bursts by maintaining a "bucket" of tokens
- Tokens refill at a fixed rate
- Binary decision: all-or-nothing
- Initial state: bucket starts FULL

**Mathematical Model:**

```
T_available = min(B, T_last + (t_current - t_last) × R)

Where:
  B         = Bucket Capacity (max tokens)
  R         = Refill Rate (tokens per millisecond)
  T_last    = Token count at last request
  t_current = Current timestamp (ms)
  t_last    = Timestamp of last request
```

**Configuration:**

```java
@RateLimit(
    algorithm = RateLimitConfig.Algorithm.TOKEN_BUCKET,
    requests = 100,       // Base rate: 100 requests per window
    window = 60,          // Window: 60 seconds
    capacity = 150,       // Burst capacity: 150 tokens (optional)
    refillRate = 1.67     // Tokens per second (optional, auto-calculated)
)
```

**When to Use:**
- Public APIs with variable traffic
- User-facing endpoints where bursts are acceptable
- When smooth rate limiting is preferred over strict enforcement

### Sliding Window Algorithm

The Sliding Window algorithm provides high accuracy without burst allowance, using a weighted two-window approach.

**Characteristics:**
- Uses weighted average of current and previous window
- More strict enforcement than Token Bucket
- O(1) memory complexity (only 2 windows)
- Minimum time unit: 1 second

**Mathematical Model:**

```
Rate = (Previous_Count × Overlap_Weight) + Current_Count

Where:
  Overlap_Weight = (Window_Size - Time_Elapsed_In_Current) / Window_Size
```

**Configuration:**

```java
@RateLimit(
    algorithm = RateLimitConfig.Algorithm.SLIDING_WINDOW,
    requests = 100,
    window = 60
)
```

**When to Use:**
- Strict rate limiting requirements
- Expensive operations (database writes, external API calls)
- When preventing request clustering at window boundaries is critical

### Algorithm Comparison

| Feature | Token Bucket | Sliding Window |
|---------|--------------|----------------|
| Burst Handling | Allows bursts up to capacity | No burst allowance |
| Accuracy | Lower (burst-tolerant) | Higher (strict) |
| Memory | O(1) | O(1) |
| Complexity | O(1) | O(1) |
| Best For | User-facing APIs | Strict enforcement |

---

## Annotation API

### @RateLimit

The primary annotation for applying rate limiting to methods or classes.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * Rate limiter name (defaults to method name).
     */
    String name() default "";

    /**
     * Key expression (SpEL supported).
     * Available variables: #ip, #user, #args, #headers, #request
     */
    String key() default "#ip";

    /**
     * Rate limiting algorithm.
     */
    RateLimitConfig.Algorithm algorithm() default Algorithm.TOKEN_BUCKET;

    /**
     * Maximum requests allowed in the window.
     */
    int requests();

    /**
     * Time window duration.
     */
    long window();

    /**
     * Time unit for window (default: SECONDS).
     */
    TimeUnit windowUnit() default TimeUnit.SECONDS;

    /**
     * Failure strategy when storage unavailable.
     */
    RateLimitConfig.FailStrategy failStrategy() default FailStrategy.FAIL_OPEN;

    /**
     * Token bucket capacity (default: same as requests).
     */
    int capacity() default -1;

    /**
     * Token refill rate per second (default: auto-calculated).
     */
    double refillRate() default -1.0;
}
```

### @RateLimits (Tiered Limiting)

Apply multiple rate limits to a single endpoint:

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

**Method Level (Recommended):**

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
    public Data getData() {
        return service.fetchData();
    }

    @RateLimit(requests = 100, window = 60)  // Override
    @GetMapping("/other")
    public Other getOther() {
        return service.fetchOther();
    }
}
```

---

## YAML Configuration Reference

### Complete Configuration Schema

```yaml
ratelimit:
  # Global enable/disable switch
  enabled: true

  # SpEL Expression Configuration
  spel:
    # Compiler mode: IMMEDIATE, MIXED, or OFF
    # IMMEDIATE: Compile on first access (40× faster, recommended for production)
    # MIXED: Compile after several evaluations
    # OFF: Never compile (useful for debugging)
    compiler-mode: IMMEDIATE

    # Maximum number of compiled expressions to cache
    cache-size: 1000

  # Proxy Configuration (for accurate IP resolution)
  proxy:
    # Number of trusted hops from right in X-Forwarded-For
    # 0: Use rightmost IP
    # 1: Skip 1 from right (single proxy like Nginx)
    # 2: Skip 2 from right (CDN + reverse proxy)
    # 3: Skip 3 from right (CDN + ALB + reverse proxy)
    trusted-hops: 1

    # CIDR ranges of trusted proxies
    trusted-proxies:
      - "127.0.0.0/8"        # Localhost IPv4
      - "::1/128"            # Localhost IPv6
      - "10.0.0.0/8"         # Private network Class A
      - "172.16.0.0/12"      # Private network Class B (Docker/K8s)
      - "192.168.0.0/16"     # Private network Class C
      # Add your CDN/Load Balancer IPs:
      # - "104.16.0.0/12"    # Cloudflare
      # - "35.156.0.0/14"    # AWS ALB

  # Adaptive Throttling Configuration
  throttling:
    # Enable gradual slowdown before hard blocking
    enabled: false

    # Percentage of limit where throttling begins (1-99)
    soft-limit: 80

    # Maximum delay in milliseconds at hard limit
    max-delay-ms: 2000

    # Throttling strategy: LINEAR or EXPONENTIAL
    strategy: LINEAR

  # Alternative throttle config (same as throttling)
  throttle:
    enabled: false
    soft-limit-percentage: 80
    max-delay-ms: 2000
    strategy: LINEAR
    # Use non-blocking delay for reactive apps
    non-blocking: false

  # RFC 9457 Problem Details Configuration
  problem-details:
    # Enable standardized JSON error responses
    enabled: true

    # Include extension fields (limit, remaining, reset)
    include-extensions: true

    # Custom problem type URI
    type-uri: "https://ratelimit.io/probs/too-many-requests"
```

### Configuration by Environment

**Development:**

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: OFF  # Better debugging
    cache-size: 100
  proxy:
    trusted-hops: 0     # Direct connection
    trusted-proxies: []
  throttling:
    enabled: false
```

**Staging:**

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

**Production:**

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE  # Maximum performance
    cache-size: 2000
  proxy:
    trusted-hops: 2           # CDN + Reverse Proxy
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"       # Cloudflare
  throttling:
    enabled: true
    soft-limit: 80
    max-delay-ms: 2000
    strategy: LINEAR
  problem-details:
    enabled: true
    include-extensions: true
```

### Quarkus Properties Format

```properties
# Basic
ratelimit.enabled=true

# SpEL
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=1000

# Proxy
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=10.0.0.0/8,104.16.0.0/12

# Throttling
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=80
ratelimit.throttling.max-delay-ms=2000
ratelimit.throttling.strategy=LINEAR

# Problem Details
ratelimit.problem-details.enabled=true
ratelimit.problem-details.include-extensions=true
```

---

## Storage Backends

### In-Memory Storage (Caffeine)

High-performance local storage using Caffeine cache.

**Characteristics:**
- Single-node deployment
- Thread-safe, lock-free
- Automatic TTL-based cleanup
- Default: 10,000 entries, 2-hour TTL

**Configuration:**

```java
@Bean
public StorageProvider storageProvider() {
    return new CaffeineStorageProvider();
}

// Or with custom configuration
@Bean
public StorageProvider storageProvider() {
    return new CaffeineStorageProvider(
        10_000,              // maxEntries
        Duration.ofHours(2)  // ttl
    );
}
```

**When to Use:**
- Development and testing
- Single-node deployments
- Low-latency requirements (<100μs)

### Redis Storage

Distributed storage with atomic Lua scripts.

**Characteristics:**
- Cluster-wide rate limiting
- Atomic operations via Lua scripts
- Versioned scripts with SHA-1 verification
- Uses Redis TIME() for clock synchronization

**Configuration:**

```java
@Bean
public StorageProvider storageProvider(JedisPool jedisPool) {
    return new RedisStorageProvider(jedisPool);
}

// Jedis pool configuration
@Bean
public JedisPool jedisPool() {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(50);
    config.setMaxIdle(10);
    config.setMinIdle(5);
    return new JedisPool(config, "redis.example.com", 6379);
}
```

**YAML for Redis connection:**

```yaml
spring:
  redis:
    host: redis.example.com
    port: 6379
    password: ${REDIS_PASSWORD:}
    timeout: 2000
    jedis:
      pool:
        max-active: 50
        max-idle: 10
        min-idle: 5
```

**When to Use:**
- Multi-node deployments
- Distributed systems
- When global rate limits are required

### Tiered Storage (L1/L2 Failover)

Combines Redis (L1) and Caffeine (L2) for resilience.

**Characteristics:**
- L1 (Redis): Distributed consistency (CP mode)
- L2 (Caffeine): Per-node protection (AP mode)
- Automatic failover on L1 failure
- Jittered reconnection prevents thundering herd

**Configuration:**

```java
@Bean
public StorageProvider storageProvider(JedisPool jedisPool) {
    // L1: Distributed storage
    RedisStorageProvider l1 = new RedisStorageProvider(jedisPool);

    // L2: Local fallback
    CaffeineStorageProvider l2 = new CaffeineStorageProvider();

    // Tiered with FAIL_OPEN strategy
    return new TieredStorageProvider(
        l1,
        l2,
        RateLimitConfig.FailStrategy.FAIL_OPEN
    );
}
```

**Failover Behavior:**

| L1 State | Behavior | Consistency |
|----------|----------|-------------|
| Healthy | All requests to L1 | Strong (CP) |
| Failed | Requests to L2 | Eventual (AP) |
| Recovering | Jittered reconnection | Transitioning |

**CAP Trade-off:**

When L1 fails, total cluster traffic may exceed global limit by:
```
(Node_Count - 1) × Limit
```
This is acceptable for maintaining availability.

---

## Key Resolution & SpEL

### Available Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `#ip` | Client IP address | `192.168.1.100` |
| `#user` | Authenticated principal | `User{id=123}` |
| `#args` | Method arguments array | `[request, param1]` |
| `#args[0]` | First argument | `OrderRequest` |
| `#headers` | HTTP headers map | `{Authorization=...}` |
| `#request` | Request body (first arg) | Object fields accessible |

### Key Expression Examples

**Per-IP Limiting:**

```java
@RateLimit(key = "#ip", requests = 1000, window = 3600)
```

**Per-User Limiting:**

```java
@RateLimit(key = "#user.id", requests = 100, window = 60)
```

**Per-Tenant Limiting:**

```java
@RateLimit(key = "#headers['X-Tenant-ID']", requests = 5000, window = 3600)
```

**Composite Keys:**

```java
// Tenant + User combination
@RateLimit(key = "#tenant.id + ':' + #user.id", requests = 50, window = 60)

// API Key + Endpoint
@RateLimit(key = "#headers['X-API-Key'] + ':' + #methodName", requests = 100, window = 60)
```

**Request Body Fields:**

```java
@RateLimit(key = "#request.customerId", requests = 20, window = 60)
@PostMapping("/process")
public Result process(@RequestBody ProcessRequest request) {
    return processor.process(request);
}
```

**Method Arguments:**

```java
@RateLimit(key = "#args[0]", requests = 10, window = 60)
@GetMapping("/user/{userId}")
public User getUser(@PathVariable String userId) {
    return userService.findById(userId);
}
```

**Conditional Keys:**

```java
// Different limits for authenticated vs anonymous
@RateLimit(
    key = "#user != null ? 'auth:' + #user.id : 'anon:' + #ip",
    requests = 100,
    window = 60
)
```

### SpEL Performance Optimization

Configure SpEL compilation for 40× faster expression evaluation:

```yaml
ratelimit:
  spel:
    compiler-mode: IMMEDIATE  # Compile on first access
    cache-size: 1000          # Cache compiled expressions
```

**Compiler Modes:**

| Mode | Behavior | Use Case |
|------|----------|----------|
| `IMMEDIATE` | Compile on first access | Production |
| `MIXED` | Compile after several uses | Balanced |
| `OFF` | Never compile | Debugging |

---

## Security Features

### SpEL Injection Prevention

The library includes comprehensive protection against SpEL injection attacks.

**Blocked Keywords (30+):**

```
# Class access
class, classloader, forname

# Runtime
runtime, system, process, processbuilder, exec

# Reflection
method, constructor, field, invoke, reflect

# Security
securitymanager, accesscontroller

# File I/O
file, socket, url, uri

# Scripting
scriptengine, nashorn, javascript

# Additional
getenv, setproperty, beaninfo, introspector
```

**Blocked Packages:**

```
java.lang.reflect
java.lang.invoke
sun.reflect
sun.misc
jdk.internal
java.beans
javax.script
javax.management
java.rmi
javax.naming (JNDI)
java.lang.instrument
```

**Safe Types Allowed:**

- Primitive types and wrappers
- String, Number, Boolean
- Date/Time classes
- Collection types
- Custom business objects (with package restrictions)

### Sensitive Data Filtering

Automatic masking in audit logs:

```java
// These patterns are automatically masked:
password, passwd, pwd, secret, token, apikey, api_key,
api-key, auth, credential, private, bearer
```

**Example:**

```json
// Input
{"username": "john", "password": "secret123", "api_key": "abc"}

// Output (filtered)
{"username": "john", "password": "[FILTERED]", "api_key": "[FILTERED]"}
```

### IP Spoofing Prevention

Configure hop counting to prevent X-Forwarded-For spoofing:

```yaml
ratelimit:
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "10.0.0.0/8"
      - "104.16.0.0/12"
```

**How Hop Counting Works:**

```
Attack attempt:
X-Forwarded-For: spoofed_ip, real_ip, cloudflare_ip
Remote Address: nginx_ip

With trusted-hops=2:
1. Verify nginx_ip is in trusted-proxies ✓
2. Count 2 hops from right in XFF
3. Result: real_ip (correct!)

Without hop counting:
Result: spoofed_ip (WRONG - attacker controlled)
```

---

## Resilience Patterns

### Circuit Breaker

Prevents cascading failures when storage is unavailable.

**States:**

| State | Behavior | Transition |
|-------|----------|------------|
| CLOSED | Normal operation | → OPEN on failure threshold |
| OPEN | Requests blocked/fallback | → HALF_OPEN after timeout |
| HALF_OPEN | Single probe request | → CLOSED on success, OPEN on failure |

**Configuration:**

```java
JitteredCircuitBreaker circuitBreaker = new JitteredCircuitBreaker(
    0.5,     // failureThreshold: 50%
    10_000,  // windowMs: 10 seconds
    30_000,  // halfOpenTimeoutMs: 30 seconds
    0.3,     // jitterFactor: ±30%
    1        // maxConcurrentProbes
);
```

**Jitter Formula (Prevents Thundering Herd):**

```
timeout = BASE_TIMEOUT × (1 ± JITTER_FACTOR × random())

Example with 30s base and ±30% jitter:
  Node 1: 21.5s (30s × 0.72)
  Node 2: 34.2s (30s × 1.14)
  Node 3: 28.9s (30s × 0.96)
  Result: Reconnections spread over ~13 seconds
```

### Failure Strategies

**FAIL_OPEN (AP Mode - Default):**

```java
@RateLimit(
    requests = 100,
    window = 60,
    failStrategy = RateLimitConfig.FailStrategy.FAIL_OPEN
)
```

- Prioritizes **availability**
- Allows requests when storage fails
- Uses per-node fallback (L2)
- Best for: User-facing APIs

**FAIL_CLOSED (CP Mode):**

```java
@RateLimit(
    requests = 100,
    window = 60,
    failStrategy = RateLimitConfig.FailStrategy.FAIL_CLOSED
)
```

- Prioritizes **consistency**
- Denies requests when storage fails
- Returns 503 Service Unavailable
- Best for: Critical/payment APIs

---

## Advanced Features

### Adaptive Throttling

Gradual slowdown instead of hard blocking.

**Configuration:**

```yaml
ratelimit:
  throttling:
    enabled: true
    soft-limit: 80           # Start throttling at 80%
    max-delay-ms: 2000       # Max 2 second delay
    strategy: LINEAR         # or EXPONENTIAL
```

**Behavior:**

```
Usage:    0%  ─────── 80% ──────── 100% ─── 100%+
Action:  Normal    Throttle       Max Delay   REJECT
Delay:    0ms     0-2000ms        2000ms       429
```

**Strategy Comparison:**

| Usage | LINEAR | EXPONENTIAL |
|-------|--------|-------------|
| 80% | 0ms | 0ms |
| 85% | 500ms | 125ms |
| 90% | 1000ms | 400ms |
| 95% | 1500ms | 900ms |
| 100% | 2000ms | 2000ms |

**Reactive/Non-Blocking Mode:**

```yaml
ratelimit:
  throttle:
    enabled: true
    non-blocking: true  # Use CompletableFuture instead of Thread.sleep
```

### RFC 9457 Problem Details

Standardized machine-readable error responses.

**Response Format:**

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 24
RateLimit-Policy: 100;w=60
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1703635200

{
  "type": "https://ratelimit.io/probs/too-many-requests",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit 'api-requests' exceeded. Please try again in 24 seconds.",
  "instance": "/api/v1/orders",
  "retry_after": 24,
  "limit": 100,
  "remaining": 0,
  "reset": 1703635200
}
```

**Configuration:**

```yaml
ratelimit:
  problem-details:
    enabled: true
    include-extensions: true
    type-uri: "https://api.example.com/probs/rate-limit"
```

### HTTP Response Headers

The library automatically adds these headers:

| Header | Description | Example |
|--------|-------------|---------|
| `Retry-After` | Seconds until reset | `24` |
| `RateLimit-Limit` | Request limit | `100` |
| `RateLimit-Remaining` | Requests remaining | `0` |
| `RateLimit-Reset` | Unix timestamp of reset | `1703635200` |
| `RateLimit-Policy` | Policy format | `100;w=60` |

---

## Monitoring & Observability

### Prometheus Metrics

**Available Metrics:**

```prometheus
# Request counters
ratelimit_requests_total{result="allowed", limiter="api-endpoint"}
ratelimit_requests_total{result="denied", limiter="api-endpoint"}

# Latency histogram
ratelimit_latency_seconds_bucket{le="0.001"}
ratelimit_latency_seconds_bucket{le="0.005"}
ratelimit_latency_seconds_bucket{le="0.01"}

# Circuit breaker state
ratelimit_circuit_state{state="closed"}
ratelimit_circuit_state{state="open"}
ratelimit_circuit_state{state="half_open"}

# Storage health
ratelimit_storage_health{provider="redis"}

# Throttling delay (if enabled)
ratelimit_throttle_delay_seconds_bucket
```

**Grafana Queries:**

```promql
# Denial rate
rate(ratelimit_requests_total{result="denied"}[5m])

# Success rate
sum(rate(ratelimit_requests_total{result="allowed"}[5m])) /
sum(rate(ratelimit_requests_total[5m]))

# P95 latency
histogram_quantile(0.95, rate(ratelimit_latency_seconds_bucket[5m]))
```

### Audit Logging

**Logged Events:**

| Event | Description |
|-------|-------------|
| `CONFIG_CHANGE` | Rate limit configuration modified |
| `ENFORCEMENT` | Request allowed/denied |
| `STORAGE_FAILURE` | Storage provider error |
| `CIRCUIT_OPEN` | Circuit breaker tripped |
| `CIRCUIT_CLOSE` | Circuit breaker recovered |

**Configuration:**

```java
@Bean
public AuditLogger auditLogger() {
    return new Slf4jAuditLogger();
}
```

### Health Checks

**Storage Health:**

```java
StorageProvider storage = ...;
boolean healthy = storage.isHealthy();
Map<String, Object> diagnostics = storage.getDiagnostics();
```

**Diagnostics Output:**

```json
{
  "provider": "redis",
  "healthy": true,
  "connectionPoolActive": 5,
  "connectionPoolIdle": 10,
  "scriptVersion": "1.0.0",
  "scriptSha": "abc123..."
}
```

---

## Framework Integration

### Spring Boot Auto-Configuration

The library auto-configures with sensible defaults:

```java
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {
    // Auto-configured beans:
    // - StorageProvider (Caffeine by default)
    // - KeyResolver (SpEL-based)
    // - MetricsExporter (Micrometer if available)
    // - RateLimitAspect (AOP interceptor)
}
```

**Custom Bean Override:**

```java
@Configuration
public class RateLimitCustomConfig {

    @Bean
    @Primary
    public StorageProvider customStorageProvider(JedisPool jedisPool) {
        return new RedisStorageProvider(jedisPool);
    }

    @Bean
    @Primary
    public KeyResolver customKeyResolver() {
        return context -> {
            // Custom key resolution logic
            return context.getKeyExpression();
        };
    }
}
```

### Quarkus CDI Integration

```java
@ApplicationScoped
public class RateLimitProducer {

    @Produces
    @ApplicationScoped
    public StorageProvider storageProvider() {
        return new CaffeineStorageProvider();
    }
}
```

### WebFlux (Reactive) Support

```java
@RateLimit(key = "#ip", requests = 100, window = 60)
@GetMapping("/reactive")
public Mono<Data> getDataReactive() {
    return dataService.fetchReactive();
}
```

**Non-Blocking Throttling:**

```yaml
ratelimit:
  throttle:
    enabled: true
    non-blocking: true  # Required for WebFlux
```

---

## Best Practices

### 1. Choose the Right Algorithm

| Scenario | Algorithm | Reason |
|----------|-----------|--------|
| Public API | Token Bucket | Allows bursts, better UX |
| Payment API | Sliding Window | Strict enforcement |
| Search API | Token Bucket | Handle query bursts |
| Write Operations | Sliding Window | Prevent abuse |

### 2. Set Appropriate Limits

```java
// Too restrictive (bad UX)
@RateLimit(requests = 10, window = 60)

// Too permissive (no protection)
@RateLimit(requests = 100000, window = 60)

// Balanced (good default)
@RateLimit(requests = 100, window = 60)
```

### 3. Use Tiered Limits

```java
@RateLimits({
    @RateLimit(name = "burst", requests = 10, window = 1),      // Burst protection
    @RateLimit(name = "minute", requests = 60, window = 60),    // Sustained rate
    @RateLimit(name = "daily", requests = 10000, window = 86400) // Daily quota
})
```

### 4. Configure Proxy Correctly

```yaml
# WRONG: Trusts all proxies (spoofing vulnerability)
proxy:
  trusted-hops: 1
  trusted-proxies: []

# CORRECT: Only trust known proxies
proxy:
  trusted-hops: 2
  trusted-proxies:
    - "10.0.0.0/8"
    - "104.16.0.0/12"
```

### 5. Enable Adaptive Throttling for Public APIs

```yaml
ratelimit:
  throttling:
    enabled: true
    soft-limit: 80
    max-delay-ms: 2000
    strategy: LINEAR
```

### 6. Use FAIL_OPEN for Availability

```java
// User-facing API: availability > consistency
@RateLimit(
    requests = 100,
    window = 60,
    failStrategy = FailStrategy.FAIL_OPEN
)

// Payment API: consistency > availability
@RateLimit(
    requests = 10,
    window = 60,
    failStrategy = FailStrategy.FAIL_CLOSED
)
```

### 7. Monitor Key Metrics

```promql
# Alert on high denial rate
rate(ratelimit_requests_total{result="denied"}[5m]) > 0.1

# Alert on circuit breaker open
ratelimit_circuit_state{state="open"} == 1

# Alert on storage unhealthy
ratelimit_storage_health == 0
```

### 8. Test Your Configuration

```bash
# Simulate load
for i in {1..110}; do
  curl -w "Status: %{http_code}, Time: %{time_total}s\n" \
       http://localhost:8080/api/data
done

# Verify IP resolution
curl -H "X-Forwarded-For: spoofed, real" \
     http://localhost:8080/api/data
# Should use "real" IP, not "spoofed"
```

---

## Deployment Recommendations

### Small (1-5 nodes)

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE
  proxy:
    trusted-hops: 1
    trusted-proxies: ["10.0.0.0/8"]
  throttling:
    enabled: false

# Storage: In-memory (Caffeine)
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

# Storage: Redis
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

# Storage: Tiered (Redis L1 + Caffeine L2)
```

---

## Troubleshooting

### Common Issues

**1. Rate limits not applied:**

```java
// Check: Is the annotation on a public method?
// Check: Is the method being called through proxy (not internal)?
// Check: Is ratelimit.enabled=true?
```

**2. Incorrect IP resolution:**

```bash
# Verify X-Forwarded-For handling
curl -v -H "X-Forwarded-For: test" http://localhost:8080/api
# Check logs for resolved IP
```

**3. SpEL expression errors:**

```java
// Enable debug logging
logging.level.com.lycosoft.ratelimit=DEBUG

// Common mistakes:
// Wrong: key = "user.id"      (missing #)
// Right: key = "#user.id"
```

**4. Storage connection issues:**

```java
// Check storage health
boolean healthy = storageProvider.isHealthy();
Map<String, Object> diag = storageProvider.getDiagnostics();
log.info("Storage diagnostics: {}", diag);
```

---

## API Reference

### Core Interfaces

```java
// Storage Provider SPI
public interface StorageProvider {
    long getCurrentTime();
    boolean tryAcquire(String key, RateLimitConfig config, long currentTime);
    void reset(String key);
    Optional<RateLimitState> getState(String key);
    boolean isHealthy();
    Map<String, Object> getDiagnostics();
}

// Key Resolver SPI
public interface KeyResolver {
    String resolve(RateLimitContext context);
}

// Metrics Exporter SPI
public interface MetricsExporter {
    void recordAllow(String limiterName, String key);
    void recordDeny(String limiterName, String key, long retryAfterMs);
    void recordLatency(String limiterName, long latencyNanos);
    void recordUsage(String limiterName, double usagePercent);
    void recordError(String limiterName, String errorType);
}

// Audit Logger SPI
public interface AuditLogger {
    void logConfigChange(RateLimitConfig oldConfig, RateLimitConfig newConfig);
    void logEnforcement(RateLimitDecision decision);
    void logFailure(String limiterName, Throwable error);
}
```

### Rate Limit Decision

```java
public interface RateLimitDecision {
    boolean isAllowed();
    int getLimit();
    int getRemaining();
    long getResetTime();
    long getRetryAfterSeconds();
    String getLimiterName();
}
```

---

**Documentation Version:** 1.1.0
**Last Updated:** 2024
