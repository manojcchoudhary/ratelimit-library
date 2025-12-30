# Quarkus Implementation Guide

A comprehensive guide for implementing rate limiting in Quarkus applications using CDI interceptors and reactive support.

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Annotation API](#annotation-api)
4. [Properties Configuration](#properties-configuration)
5. [Key Expressions](#key-expressions)
6. [Storage Configuration](#storage-configuration)
7. [Reactive Support](#reactive-support)
8. [Advanced Features](#advanced-features)
9. [Security Features](#security-features)
10. [Monitoring & Observability](#monitoring--observability)
11. [Production Deployment](#production-deployment)
12. [Examples](#examples)
13. [Troubleshooting](#troubleshooting)

---

## Overview

The Quarkus adapter provides seamless integration with CDI interceptors, Vert.x reactive support, and native compilation compatibility.

### Features

- **CDI Integration**: Native CDI interceptor support
- **Reactive Ready**: Vert.x and Mutiny integration
- **Native Compilation**: GraalVM native-image compatible
- **Zero Configuration**: Works out of the box
- **Properties Support**: Standard Quarkus configuration

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Quarkus Application                           │
├─────────────────────────────────────────────────────────────────┤
│  @RateLimit  │  @RateLimits                                     │
│  (Annotations)                                                   │
├─────────────────────────────────────────────────────────────────┤
│  RateLimitInterceptor (CDI)                                     │
│  ├─ Expression-based Key Resolution                              │
│  ├─ IP Resolution (Hop Counting)                                 │
│  └─ Adaptive Throttling                                          │
├─────────────────────────────────────────────────────────────────┤
│  Reactive Support                                                │
│  ├─ Vert.x Integration                                           │
│  ├─ Mutiny Uni/Multi                                             │
│  └─ Non-blocking Throttling                                      │
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
    <artifactId>rl-adapter-quarkus</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- For Redis storage (optional) -->
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-spi-redis</artifactId>
    <version>1.1.0</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'com.lycosoft:rl-adapter-quarkus:1.1.0'
implementation 'com.lycosoft:rl-spi-redis:1.1.0'  // Optional
```

### 2. Apply Annotation

```java
@Path("/orders")
@ApplicationScoped
public class OrderResource {

    @RateLimit(requests = 100, window = 60)
    @GET
    public List<Order> getOrders() {
        return orderService.findAll();
    }
}
```

### 3. Configure (Optional)

```properties
# application.properties
ratelimit.enabled=true
```

That's it! The library auto-configures itself with:
- In-memory storage (Caffeine)
- Default key: `#ip` (client IP address)

---

## Annotation API

### @RateLimit

The primary annotation for rate limiting methods.

```java
@RateLimit(
    name = "api-endpoint",                           // Optional name
    key = "#securityIdentity.principal.name",        // Key expression
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
| `key` | String | `"#ip"` | Expression for rate limit key |
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
@GET
@Path("/resource")
public Resource getResource() {
    return resourceService.fetch();
}
```

### Annotation Placement

**Method Level:**

```java
@Path("/api")
@ApplicationScoped
public class ApiResource {

    @RateLimit(requests = 100, window = 60)
    @GET
    @Path("/data")
    public Data getData() {
        return service.fetchData();
    }
}
```

**Class Level (Default for all methods):**

```java
@RateLimit(requests = 50, window = 60)
@Path("/api")
@ApplicationScoped
public class ApiResource {

    @GET
    @Path("/data")  // Inherits class-level limit
    public Data getData() { ... }

    @RateLimit(requests = 100, window = 60)  // Override
    @GET
    @Path("/other")
    public Other getOther() { ... }
}
```

---

## Properties Configuration

### Complete Reference

```properties
# Master enable/disable switch
ratelimit.enabled=true

# Expression compilation for performance
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=1000

# Proxy Configuration (IP Resolution)
# Number of trusted hops from right in X-Forwarded-For
# 0: Use rightmost IP
# 1: Single proxy (Nginx)
# 2: CDN + proxy (Cloudflare + Nginx)
ratelimit.proxy.trusted-hops=1

# CIDR ranges of trusted proxies (comma-separated)
ratelimit.proxy.trusted-proxies=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16

# Adaptive Throttling
ratelimit.throttling.enabled=false
ratelimit.throttling.soft-limit=80
ratelimit.throttling.max-delay-ms=2000
ratelimit.throttling.strategy=LINEAR

# RFC 9457 Problem Details
ratelimit.problem-details.enabled=true
ratelimit.problem-details.include-extensions=true
ratelimit.problem-details.type-uri=https://api.example.com/probs/rate-limit
```

### Environment-Specific Configuration

**application.properties (default):**

```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=IMMEDIATE
```

**application-dev.properties:**

```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=OFF
ratelimit.proxy.trusted-hops=0
ratelimit.throttling.enabled=false
```

**application-prod.properties:**

```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=2000
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=10.0.0.0/8,104.16.0.0/12
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=80
ratelimit.throttling.max-delay-ms=2000
ratelimit.problem-details.enabled=true
```

### YAML Configuration (application.yaml)

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
    strategy: LINEAR

  problem-details:
    enabled: true
    include-extensions: true
```

---

## Key Expressions

### Available Variables

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `#ip` | Client IP address | `"192.168.1.100"` |
| `#securityIdentity` | Quarkus SecurityIdentity | Identity object |
| `#args` | Method arguments array | `[request, param1]` |
| `#args[0]` | First argument | `OrderRequest` |
| `#headers` | HTTP headers map | `{Authorization=...}` |
| `#request` | First @RequestBody argument | Object fields |

### Common Patterns

**Per-IP Limiting:**

```java
@RateLimit(key = "#ip", requests = 1000, window = 3600)
@GET
@Path("/public")
public Data getPublicData() { ... }
```

**Per-User Limiting:**

```java
@RateLimit(key = "#securityIdentity.principal.name", requests = 100, window = 60)
@POST
@Path("/orders")
public Order createOrder(OrderRequest request) { ... }
```

**Per-Tenant Limiting:**

```java
@RateLimit(key = "#headers['X-Tenant-ID']", requests = 5000, window = 3600)
@GET
@Path("/tenant/data")
public TenantData getTenantData() { ... }
```

**Composite Keys:**

```java
// Tenant + User
@RateLimit(
    key = "#headers['X-Tenant-ID'] + ':' + #securityIdentity.principal.name",
    requests = 50,
    window = 60
)

// API Key + Endpoint
@RateLimit(
    key = "#headers['X-API-Key'] + ':orders'",
    requests = 100,
    window = 60
)
```

**Request Body Fields:**

```java
@RateLimit(key = "#request.customerId", requests = 20, window = 60)
@POST
@Path("/process")
public Result process(ProcessRequest request) { ... }
```

**Path Parameters:**

```java
@RateLimit(key = "'resource:' + #args[0]", requests = 50, window = 60)
@GET
@Path("/resource/{resourceId}")
public Resource getResource(@PathParam("resourceId") String resourceId) { ... }
```

---

## Storage Configuration

### In-Memory (Default)

Uses Caffeine cache - no additional configuration needed.

```java
@ApplicationScoped
public class StorageProducer {

    @Produces
    @ApplicationScoped
    public StorageProvider storageProvider() {
        return new CaffeineStorageProvider();
    }
}
```

### Redis Storage

**1. Add Dependencies:**

```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-spi-redis</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-redis-client</artifactId>
</dependency>
```

**2. Configure Redis:**

```properties
quarkus.redis.hosts=redis://localhost:6379
quarkus.redis.password=${REDIS_PASSWORD:}
quarkus.redis.timeout=2s
```

**3. Create Producer:**

```java
@ApplicationScoped
public class StorageProducer {

    @Inject
    RedisClient redisClient;

    @Produces
    @ApplicationScoped
    public StorageProvider storageProvider() {
        // Create Jedis pool from Quarkus Redis config
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);

        JedisPool jedisPool = new JedisPool(poolConfig,
            ConfigProvider.getConfig()
                .getValue("quarkus.redis.hosts", String.class)
                .replace("redis://", "")
                .split(":")[0],
            6379
        );

        return new RedisStorageProvider(jedisPool);
    }
}
```

### Tiered Storage (Redis + Caffeine)

```java
@ApplicationScoped
public class StorageProducer {

    @Produces
    @ApplicationScoped
    public StorageProvider storageProvider() {
        // L1: Redis (distributed)
        JedisPool jedisPool = createJedisPool();
        RedisStorageProvider l1 = new RedisStorageProvider(jedisPool);

        // L2: Caffeine (local fallback)
        CaffeineStorageProvider l2 = new CaffeineStorageProvider();

        // Tiered with fail-open strategy
        return new TieredStorageProvider(
            l1,
            l2,
            RateLimitConfig.FailStrategy.FAIL_OPEN
        );
    }

    private JedisPool createJedisPool() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(10);
        config.setMinIdle(5);

        String redisHost = ConfigProvider.getConfig()
            .getValue("app.redis.host", String.class);
        int redisPort = ConfigProvider.getConfig()
            .getValue("app.redis.port", Integer.class);

        return new JedisPool(config, redisHost, redisPort);
    }
}
```

---

## Reactive Support

### Mutiny Uni

```java
@Path("/reactive")
@ApplicationScoped
public class ReactiveResource {

    @RateLimit(key = "#ip", requests = 100, window = 60)
    @GET
    @Path("/data")
    public Uni<Data> getData() {
        return dataService.fetchAsync();
    }

    @RateLimit(key = "#securityIdentity.principal.name", requests = 50, window = 60)
    @POST
    @Path("/process")
    public Uni<Result> process(ProcessRequest request) {
        return processor.processAsync(request);
    }
}
```

### Mutiny Multi (Streaming)

```java
@Path("/stream")
@ApplicationScoped
public class StreamResource {

    @RateLimit(key = "#ip", requests = 10, window = 60)
    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<Event> streamEvents() {
        return eventService.streamEvents();
    }
}
```

### Non-Blocking Throttling

For reactive applications, enable non-blocking throttling:

```properties
ratelimit.throttle.enabled=true
ratelimit.throttle.non-blocking=true
```

**Reactive Throttle Handler:**

```java
@ApplicationScoped
public class ReactiveThrottleHandler {

    public Uni<Void> applyDelay(long delayMs) {
        if (delayMs <= 0) {
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.ofMillis(delayMs));
    }
}
```

---

## Advanced Features

### Adaptive Throttling

```properties
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=80
ratelimit.throttling.max-delay-ms=2000
ratelimit.throttling.strategy=LINEAR
```

**Behavior:**

| Usage | Response | Delay |
|-------|----------|-------|
| 0-80% | Normal | 0ms |
| 80-100% | Throttled | 0-2000ms |
| >100% | Rejected | 429 Error |

### RFC 9457 Problem Details

```properties
ratelimit.problem-details.enabled=true
ratelimit.problem-details.include-extensions=true
```

**Response Format:**

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 24

{
  "type": "https://ratelimit.io/probs/too-many-requests",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded. Retry in 24 seconds.",
  "retry_after": 24,
  "limit": 100,
  "remaining": 0,
  "reset": 1703635200
}
```

### Custom Exception Mapper

```java
@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {

    @Override
    public Response toResponse(RateLimitExceededException ex) {
        Map<String, Object> problem = new HashMap<>();
        problem.put("type", "https://api.example.com/probs/rate-limit");
        problem.put("title", "Too Many Requests");
        problem.put("status", 429);
        problem.put("detail", "Rate limit exceeded. Retry in " +
            ex.getRetryAfterSeconds() + " seconds.");
        problem.put("retry_after", ex.getRetryAfterSeconds());
        problem.put("limit", ex.getLimit());
        problem.put("remaining", 0);

        return Response.status(Response.Status.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.getRetryAfterSeconds())
            .header("X-RateLimit-Limit", ex.getLimit())
            .header("X-RateLimit-Remaining", 0)
            .entity(problem)
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
```

### Hop Counting

```properties
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=10.0.0.0/8,104.16.0.0/12
```

---

## Security Features

### Expression Injection Prevention

The library blocks dangerous patterns automatically:

```java
// Safe - accessing identity
@RateLimit(key = "#securityIdentity.principal.name")

// Safe - accessing headers
@RateLimit(key = "#headers['X-API-Key']")

// BLOCKED - dangerous patterns
@RateLimit(key = "T(java.lang.Runtime).getRuntime()")  // Blocked!
```

### Sensitive Data Filtering

Automatic masking in audit logs for:
- `password`, `passwd`, `pwd`
- `secret`, `token`, `apikey`
- `auth`, `credential`, `bearer`

### Quarkus Security Integration

```java
@Path("/secure")
@Authenticated
@ApplicationScoped
public class SecureResource {

    @Inject
    SecurityIdentity securityIdentity;

    @RateLimit(
        key = "#securityIdentity.principal.name",
        requests = 100,
        window = 60
    )
    @GET
    @Path("/data")
    public Data getSecureData() {
        return dataService.fetchForUser(securityIdentity.getPrincipal().getName());
    }
}
```

---

## Monitoring & Observability

### Micrometer Metrics

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.registry-enabled-default=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/q/metrics
```

**Available Metrics:**

```prometheus
# Request counters
ratelimit_requests_total{result="allowed", limiter="api-orders"}
ratelimit_requests_total{result="denied", limiter="api-orders"}

# Latency histogram
ratelimit_latency_seconds_bucket{limiter="api-orders", le="0.001"}

# Circuit breaker
ratelimit_circuit_state{state="closed"}
ratelimit_circuit_state{state="open"}
```

### Health Check

```java
@Liveness
@ApplicationScoped
public class RateLimitHealthCheck implements HealthCheck {

    @Inject
    StorageProvider storageProvider;

    @Override
    public HealthCheckResponse call() {
        if (storageProvider.isHealthy()) {
            return HealthCheckResponse.up("ratelimit-storage");
        }
        return HealthCheckResponse.down("ratelimit-storage");
    }
}
```

```properties
quarkus.health.extensions.enabled=true
```

### Logging

```properties
quarkus.log.category."com.lycosoft.ratelimit".level=DEBUG
```

---

## Production Deployment

### Small (1-5 nodes)

```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.proxy.trusted-hops=1
ratelimit.proxy.trusted-proxies=10.0.0.0/8
ratelimit.throttling.enabled=false

# Storage: Default Caffeine (in-memory)
```

### Medium (5-50 nodes)

```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=1000
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=10.0.0.0/8,104.16.0.0/12
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=80

# Redis
quarkus.redis.hosts=redis://redis.internal:6379
```

### Large (50+ nodes)

```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=2000
ratelimit.proxy.trusted-hops=3
ratelimit.proxy.trusted-proxies=10.0.0.0/8,104.16.0.0/12,35.156.0.0/14
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=85
ratelimit.throttling.max-delay-ms=3000
ratelimit.throttling.strategy=EXPONENTIAL

# Redis Cluster
quarkus.redis.hosts=redis://redis-1:6379,redis://redis-2:6379,redis://redis-3:6379

# Storage: Tiered (Redis L1 + Caffeine L2)
```

### Native Compilation

```properties
# Ensure reflection is configured for native builds
quarkus.native.additional-build-args=--initialize-at-run-time=com.lycosoft.ratelimit
```

---

## Examples

### REST API Resource

```java
@Path("/api/v1")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiResource {

    @Inject
    OrderService orderService;

    @RateLimits({
        @RateLimit(name = "burst", key = "#ip", requests = 10, window = 1),
        @RateLimit(name = "minute", key = "#ip", requests = 100, window = 60)
    })
    @GET
    @Path("/orders")
    public List<Order> getOrders() {
        return orderService.findAll();
    }

    @RateLimit(
        key = "#securityIdentity.principal.name",
        requests = 10,
        window = 60,
        algorithm = Algorithm.SLIDING_WINDOW,
        failStrategy = FailStrategy.FAIL_CLOSED
    )
    @POST
    @Path("/orders")
    @Authenticated
    public Order createOrder(OrderRequest request) {
        return orderService.create(request);
    }
}
```

### Reactive REST Resource

```java
@Path("/reactive")
@ApplicationScoped
public class ReactiveApiResource {

    @Inject
    ReactiveOrderService orderService;

    @RateLimit(key = "#ip", requests = 100, window = 60)
    @GET
    @Path("/orders")
    public Uni<List<Order>> getOrders() {
        return orderService.findAllAsync();
    }

    @RateLimit(
        key = "#securityIdentity.principal.name",
        requests = 50,
        window = 60
    )
    @POST
    @Path("/orders")
    @Authenticated
    public Uni<Order> createOrder(OrderRequest request) {
        return orderService.createAsync(request);
    }

    @RateLimit(key = "#ip", requests = 5, window = 60)
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<Event> streamEvents() {
        return eventService.streamEvents();
    }
}
```

### Multi-Tenant Resource

```java
@Path("/tenant")
@ApplicationScoped
public class TenantResource {

    @RateLimits({
        @RateLimit(
            name = "tenant-limit",
            key = "#headers['X-Tenant-ID']",
            requests = 10000,
            window = 3600
        ),
        @RateLimit(
            name = "user-limit",
            key = "#headers['X-Tenant-ID'] + ':' + #securityIdentity.principal.name",
            requests = 100,
            window = 60
        )
    })
    @GET
    @Path("/data")
    @Authenticated
    public TenantData getData(@HeaderParam("X-Tenant-ID") String tenantId) {
        return tenantService.getData(tenantId);
    }
}
```

### Webhook Resource

```java
@Path("/webhooks")
@ApplicationScoped
public class WebhookResource {

    @RateLimit(
        key = "#request.callbackUrl",
        requests = 100,
        window = 60,
        algorithm = Algorithm.SLIDING_WINDOW
    )
    @POST
    @Path("/register")
    public WebhookRegistration register(WebhookRequest request) {
        return webhookService.register(request);
    }
}
```

### GraphQL Resource

```java
@GraphQLApi
@ApplicationScoped
public class GraphQLResource {

    @RateLimit(key = "#ip", requests = 100, window = 60)
    @Query
    public List<Order> orders() {
        return orderService.findAll();
    }

    @RateLimit(
        key = "#securityIdentity.principal.name",
        requests = 20,
        window = 60
    )
    @Mutation
    public Order createOrder(OrderInput input) {
        return orderService.create(input);
    }
}
```

---

## Troubleshooting

### Rate Limits Not Applied

**1. Check CDI Scope:**

```java
// Wrong: Not a CDI bean
public class MyResource {
    @RateLimit(requests = 10, window = 60)
    public void doSomething() { ... }
}

// Right: CDI bean with scope
@ApplicationScoped
public class MyResource {
    @RateLimit(requests = 10, window = 60)
    public void doSomething() { ... }
}
```

**2. Check Self-Invocation:**

```java
// Wrong: Self-invocation bypasses interceptor
public void methodA() {
    methodB();  // Rate limit NOT applied!
}

@RateLimit(requests = 10, window = 60)
public void methodB() { ... }

// Right: Inject self
@Inject
MyResource self;

public void methodA() {
    self.methodB();  // Rate limit applied
}
```

**3. Verify Configuration:**

```properties
ratelimit.enabled=true  # Must be true
```

### Expression Errors

```java
// Wrong: Invalid expression
@RateLimit(key = "user.id")  // Error!

// Right: Valid expression
@RateLimit(key = "#args[0].userId")

// Wrong: Null value
@RateLimit(key = "#securityIdentity.principal.name")  // Null if not authenticated!

// Right: Null-safe
@RateLimit(key = "#securityIdentity?.principal?.name ?: #ip")
```

### Native Compilation Issues

```properties
# Add to application.properties
quarkus.native.additional-build-args=\
  --initialize-at-run-time=com.lycosoft.ratelimit,\
  --allow-incomplete-classpath
```

### Debug Logging

```properties
quarkus.log.category."com.lycosoft.ratelimit".level=DEBUG
```

---

## API Reference

### CDI Producers

```java
@ApplicationScoped
public class RateLimitProducers {

    @Produces
    @ApplicationScoped
    public StorageProvider storageProvider() {
        return new CaffeineStorageProvider();
    }

    @Produces
    @ApplicationScoped
    public MetricsExporter metricsExporter(MeterRegistry registry) {
        return new MicrometerMetricsExporter(registry);
    }

    @Produces
    @ApplicationScoped
    public AuditLogger auditLogger() {
        return new Slf4jAuditLogger();
    }
}
```

### Configuration Properties

```java
@ConfigMapping(prefix = "ratelimit")
public interface RateLimitConfig {

    @WithDefault("true")
    boolean enabled();

    SpelConfig spel();
    ProxyConfig proxy();
    ThrottlingConfig throttling();

    interface SpelConfig {
        @WithDefault("IMMEDIATE")
        String compilerMode();

        @WithDefault("1000")
        int cacheSize();
    }

    interface ProxyConfig {
        @WithDefault("1")
        int trustedHops();

        Optional<List<String>> trustedProxies();
    }

    interface ThrottlingConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("80")
        int softLimit();

        @WithDefault("2000")
        long maxDelayMs();

        @WithDefault("LINEAR")
        String strategy();
    }
}
```

---

## Comparison: Spring Boot vs Quarkus

| Feature | Spring Boot | Quarkus |
|---------|-------------|---------|
| Interceptor Type | AOP Aspect | CDI Interceptor |
| Configuration | YAML / Properties | Properties / YAML |
| User Identity | `@AuthenticationPrincipal` | `SecurityIdentity` |
| Reactive | WebFlux | Mutiny Uni/Multi |
| Native | Not supported | GraalVM native-image |
| Metrics | Micrometer auto-config | Manual integration |

---

**Documentation Version:** 1.1.0
