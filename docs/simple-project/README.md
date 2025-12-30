# Simple Project Implementation Guide

A comprehensive guide for implementing rate limiting in standalone Java applications without any framework.

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Core Concepts](#core-concepts)
4. [Rate Limiting Algorithms](#rate-limiting-algorithms)
5. [Storage Backends](#storage-backends)
6. [Configuration](#configuration)
7. [Complete Examples](#complete-examples)
8. [Security Features](#security-features)
9. [Resilience Patterns](#resilience-patterns)
10. [Monitoring](#monitoring)
11. [Best Practices](#best-practices)

---

## Overview

The `rl-core` module provides a framework-agnostic rate limiting solution with zero external dependencies. This guide covers how to implement rate limiting in plain Java applications, batch processors, CLI tools, or any non-framework environment.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Application                          │
├─────────────────────────────────────────────────────────────┤
│                       rl-core                                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐│
│  │ LimiterEngine│ │ Algorithms  │ │ Security/Resilience    ││
│  └─────────────┘ └─────────────┘ └─────────────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                   Storage Provider                           │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │ CaffeineStorage │  │  RedisStorage   │                   │
│  │   (In-Memory)   │  │  (Distributed)  │                   │
│  └─────────────────┘  └─────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

### When to Use

- Standalone Java applications
- Batch processors
- CLI tools
- Custom frameworks
- Libraries that need rate limiting
- Testing rate limiting logic

---

## Getting Started

### Dependencies

**Maven:**

```xml
<!-- Core module (zero dependencies) -->
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-core</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- In-memory storage (optional) -->
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-spi-caffeine</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- Redis storage (optional) -->
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-spi-redis</artifactId>
    <version>1.1.0</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'com.lycosoft:rl-core:1.1.0'
implementation 'com.lycosoft:rl-spi-caffeine:1.1.0'  // Optional
implementation 'com.lycosoft:rl-spi-redis:1.1.0'     // Optional
```

### Quick Start

```java
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.context.RateLimitContext;
import com.lycosoft.ratelimit.decision.RateLimitDecision;
import com.lycosoft.ratelimit.storage.caffeine.CaffeineStorageProvider;

public class QuickStart {
    public static void main(String[] args) {
        // 1. Create storage provider
        StorageProvider storage = new CaffeineStorageProvider();

        // 2. Create key resolver
        KeyResolver keyResolver = context -> context.getKeyExpression();

        // 3. Create engine
        LimiterEngine engine = new LimiterEngine(storage, keyResolver, null, null);

        // 4. Define rate limit configuration
        RateLimitConfig config = RateLimitConfig.builder()
            .name("api-limiter")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();

        // 5. Check rate limit
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("user:123")
            .build();

        RateLimitDecision decision = engine.tryAcquire(context, config);

        if (decision.isAllowed()) {
            System.out.println("Request allowed!");
            System.out.println("Remaining: " + decision.getRemaining());
        } else {
            System.out.println("Rate limited!");
            System.out.println("Retry after: " + decision.getRetryAfterSeconds() + "s");
        }
    }
}
```

---

## Core Concepts

### LimiterEngine

The central class that coordinates rate limiting decisions.

```java
public class LimiterEngine {

    /**
     * Create a new limiter engine.
     *
     * @param storageProvider Storage backend
     * @param keyResolver     Key resolution strategy
     * @param metricsExporter Metrics exporter (optional, can be null)
     * @param auditLogger     Audit logger (optional, can be null)
     */
    public LimiterEngine(
        StorageProvider storageProvider,
        KeyResolver keyResolver,
        MetricsExporter metricsExporter,
        AuditLogger auditLogger
    );

    /**
     * Try to acquire a permit for the given context.
     *
     * @param context Rate limit context
     * @param config  Rate limit configuration
     * @return Decision indicating if request is allowed
     */
    public RateLimitDecision tryAcquire(RateLimitContext context, RateLimitConfig config);
}
```

### RateLimitConfig

Immutable configuration for a rate limiter.

```java
RateLimitConfig config = RateLimitConfig.builder()
    .name("my-limiter")                              // Required: unique name
    .algorithm(Algorithm.TOKEN_BUCKET)               // Algorithm type
    .requests(100)                                   // Max requests
    .window(60)                                      // Window duration
    .windowUnit(TimeUnit.SECONDS)                    // Window unit
    .failStrategy(FailStrategy.FAIL_OPEN)            // Failure behavior
    .capacity(150)                                   // Token bucket capacity
    .refillRate(1.67)                                // Tokens per second
    .build();
```

### RateLimitContext

Context for a rate limit check.

```java
RateLimitContext context = RateLimitContext.builder()
    .keyExpression("user:123")                       // Rate limit key
    .clientIp("192.168.1.100")                       // Client IP (optional)
    .build();
```

### RateLimitDecision

Result of a rate limit check.

```java
RateLimitDecision decision = engine.tryAcquire(context, config);

boolean allowed = decision.isAllowed();              // Request allowed?
int limit = decision.getLimit();                     // Total limit
int remaining = decision.getRemaining();             // Requests remaining
long reset = decision.getResetTime();                // Reset timestamp (ms)
long retryAfter = decision.getRetryAfterSeconds();   // Retry after (seconds)
String limiterName = decision.getLimiterName();      // Limiter name
```

---

## Rate Limiting Algorithms

### Token Bucket Algorithm

Best for APIs that need to handle traffic bursts.

```java
RateLimitConfig tokenBucket = RateLimitConfig.builder()
    .name("token-bucket-limiter")
    .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
    .requests(100)        // 100 requests per minute
    .window(60)
    .windowUnit(TimeUnit.SECONDS)
    .capacity(150)        // Allow bursts up to 150 (optional)
    .refillRate(1.67)     // 100/60 tokens per second (optional)
    .build();
```

**How It Works:**

```
Initial State:      Bucket = 100 tokens (full)
Request arrives:    Consume 1 token
                    Bucket = 99 tokens
                    Decision: ALLOWED

After 100 requests: Bucket = 0 tokens
                    Decision: DENIED (retry after refill)

After 1 second:     Bucket = 1.67 tokens (refilled)
                    Decision: ALLOWED (1 token consumed)
```

**Characteristics:**
- Allows smooth bursts up to capacity
- Lazy calculation (no background threads)
- O(1) time complexity

### Sliding Window Algorithm

Best for strict rate enforcement without bursts.

```java
RateLimitConfig slidingWindow = RateLimitConfig.builder()
    .name("sliding-window-limiter")
    .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
    .requests(100)
    .window(60)
    .windowUnit(TimeUnit.SECONDS)
    .build();
```

**How It Works:**

```
Current window:  [0-60s]  -> 60 requests
Previous window: [-60-0s] -> 80 requests

At t=30s (50% into current window):
  Overlap weight = (60 - 30) / 60 = 0.5
  Effective count = 60 + (80 × 0.5) = 100

Request arrives:
  If effective count >= 100: DENIED
  Else: ALLOWED, increment current window
```

**Characteristics:**
- High accuracy (no boundary issues)
- No burst allowance
- O(1) memory (only 2 windows)

### Algorithm Comparison

| Scenario | Use Token Bucket | Use Sliding Window |
|----------|------------------|-------------------|
| Public API | ✓ | |
| User-facing endpoints | ✓ | |
| Database writes | | ✓ |
| External API calls | | ✓ |
| Search queries | ✓ | |
| Payment processing | | ✓ |

---

## Storage Backends

### In-Memory Storage (Caffeine)

High-performance local storage.

```java
import com.lycosoft.ratelimit.storage.caffeine.CaffeineStorageProvider;

// Default configuration
StorageProvider storage = new CaffeineStorageProvider();

// Custom configuration
StorageProvider storage = new CaffeineStorageProvider(
    10_000,                    // maxEntries
    Duration.ofHours(2)        // ttl
);
```

**Characteristics:**
- Thread-safe, lock-free
- Automatic TTL-based cleanup
- O(1) operations
- Best for single-node deployments

### Redis Storage

Distributed storage with atomic operations.

```java
import com.lycosoft.ratelimit.storage.redis.RedisStorageProvider;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

// Create Jedis pool
JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(50);
poolConfig.setMaxIdle(10);
poolConfig.setMinIdle(5);

JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379);

// Create storage provider
StorageProvider storage = new RedisStorageProvider(jedisPool);
```

**Characteristics:**
- Cluster-wide rate limiting
- Atomic Lua scripts
- Versioned script management
- Uses Redis TIME() for clock sync

### Tiered Storage (L1/L2 Failover)

Combines Redis and Caffeine for resilience.

```java
import com.lycosoft.ratelimit.resilience.TieredStorageProvider;

// L1: Distributed storage
RedisStorageProvider l1 = new RedisStorageProvider(jedisPool);

// L2: Local fallback
CaffeineStorageProvider l2 = new CaffeineStorageProvider();

// Tiered storage with fail-open strategy
StorageProvider storage = new TieredStorageProvider(
    l1,
    l2,
    RateLimitConfig.FailStrategy.FAIL_OPEN
);
```

**Failover Behavior:**

| L1 State | Behavior | Consistency |
|----------|----------|-------------|
| Healthy | All requests to L1 | Strong (CP) |
| Failed | Automatic fallback to L2 | Eventual (AP) |
| Recovering | Jittered reconnection | Transitioning |

---

## Configuration

### Programmatic Configuration

```java
// Define multiple rate limit configurations
Map<String, RateLimitConfig> configs = new HashMap<>();

configs.put("api-public", RateLimitConfig.builder()
    .name("api-public")
    .algorithm(Algorithm.TOKEN_BUCKET)
    .requests(1000)
    .window(1)
    .windowUnit(TimeUnit.HOURS)
    .capacity(1500)
    .build());

configs.put("api-auth", RateLimitConfig.builder()
    .name("api-auth")
    .algorithm(Algorithm.TOKEN_BUCKET)
    .requests(100)
    .window(1)
    .windowUnit(TimeUnit.MINUTES)
    .build());

configs.put("api-write", RateLimitConfig.builder()
    .name("api-write")
    .algorithm(Algorithm.SLIDING_WINDOW)
    .requests(20)
    .window(1)
    .windowUnit(TimeUnit.MINUTES)
    .failStrategy(FailStrategy.FAIL_CLOSED)
    .build());
```

### Configuration from Properties

```java
import java.util.Properties;

public class ConfigLoader {

    public static RateLimitConfig fromProperties(Properties props, String prefix) {
        return RateLimitConfig.builder()
            .name(props.getProperty(prefix + ".name"))
            .algorithm(Algorithm.valueOf(
                props.getProperty(prefix + ".algorithm", "TOKEN_BUCKET")))
            .requests(Integer.parseInt(
                props.getProperty(prefix + ".requests", "100")))
            .window(Long.parseLong(
                props.getProperty(prefix + ".window", "60")))
            .windowUnit(TimeUnit.valueOf(
                props.getProperty(prefix + ".windowUnit", "SECONDS")))
            .failStrategy(FailStrategy.valueOf(
                props.getProperty(prefix + ".failStrategy", "FAIL_OPEN")))
            .build();
    }
}

// Usage
Properties props = new Properties();
props.load(new FileInputStream("ratelimit.properties"));

RateLimitConfig config = ConfigLoader.fromProperties(props, "ratelimit.api");
```

**ratelimit.properties:**

```properties
ratelimit.api.name=api-limiter
ratelimit.api.algorithm=TOKEN_BUCKET
ratelimit.api.requests=100
ratelimit.api.window=60
ratelimit.api.windowUnit=SECONDS
ratelimit.api.failStrategy=FAIL_OPEN
```

### Configuration from YAML

```java
import org.yaml.snakeyaml.Yaml;

public class YamlConfigLoader {

    public static RateLimitConfig fromYaml(String yamlContent, String name) {
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(yamlContent);
        Map<String, Object> limitConfig = (Map<String, Object>)
            ((Map<String, Object>) config.get("ratelimit")).get(name);

        return RateLimitConfig.builder()
            .name(name)
            .algorithm(Algorithm.valueOf(
                (String) limitConfig.getOrDefault("algorithm", "TOKEN_BUCKET")))
            .requests((Integer) limitConfig.get("requests"))
            .window(((Number) limitConfig.get("window")).longValue())
            .windowUnit(TimeUnit.valueOf(
                (String) limitConfig.getOrDefault("windowUnit", "SECONDS")))
            .build();
    }
}
```

**ratelimit.yaml:**

```yaml
ratelimit:
  api-public:
    algorithm: TOKEN_BUCKET
    requests: 1000
    window: 3600
    windowUnit: SECONDS

  api-auth:
    algorithm: TOKEN_BUCKET
    requests: 100
    window: 60
    windowUnit: SECONDS

  api-write:
    algorithm: SLIDING_WINDOW
    requests: 20
    window: 60
    windowUnit: SECONDS
    failStrategy: FAIL_CLOSED
```

---

## Complete Examples

### HTTP Server Rate Limiting

```java
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class RateLimitedHttpServer {

    private final LimiterEngine engine;
    private final RateLimitConfig config;

    public RateLimitedHttpServer() {
        StorageProvider storage = new CaffeineStorageProvider();
        KeyResolver keyResolver = context -> context.getKeyExpression();
        this.engine = new LimiterEngine(storage, keyResolver, null, null);

        this.config = RateLimitConfig.builder()
            .name("http-api")
            .algorithm(Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();
    }

    public void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api", this::handleRequest);
        server.start();
        System.out.println("Server started on port 8080");
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String clientIp = getClientIp(exchange);

        RateLimitContext context = RateLimitContext.builder()
            .keyExpression(clientIp)
            .clientIp(clientIp)
            .build();

        RateLimitDecision decision = engine.tryAcquire(context, config);

        // Add rate limit headers
        exchange.getResponseHeaders().add("X-RateLimit-Limit",
            String.valueOf(decision.getLimit()));
        exchange.getResponseHeaders().add("X-RateLimit-Remaining",
            String.valueOf(decision.getRemaining()));
        exchange.getResponseHeaders().add("X-RateLimit-Reset",
            String.valueOf(decision.getResetTime() / 1000));

        if (!decision.isAllowed()) {
            exchange.getResponseHeaders().add("Retry-After",
                String.valueOf(decision.getRetryAfterSeconds()));

            String response = "Rate limit exceeded. Try again in " +
                decision.getRetryAfterSeconds() + " seconds";
            exchange.sendResponseHeaders(429, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
            return;
        }

        // Process request
        String response = "Hello, World!";
        exchange.sendResponseHeaders(200, response.length());
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }

    private String getClientIp(HttpExchange exchange) {
        String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    public static void main(String[] args) throws Exception {
        new RateLimitedHttpServer().start();
    }
}
```

### Batch Processor with Rate Limiting

```java
public class RateLimitedBatchProcessor {

    private final LimiterEngine engine;
    private final RateLimitConfig config;

    public RateLimitedBatchProcessor() {
        StorageProvider storage = new CaffeineStorageProvider();
        KeyResolver keyResolver = context -> context.getKeyExpression();
        this.engine = new LimiterEngine(storage, keyResolver, null, null);

        // Limit to 100 API calls per minute
        this.config = RateLimitConfig.builder()
            .name("batch-processor")
            .algorithm(Algorithm.TOKEN_BUCKET)
            .requests(100)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();
    }

    public void processItems(List<Item> items) {
        for (Item item : items) {
            processWithRateLimit(item);
        }
    }

    private void processWithRateLimit(Item item) {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("batch-processor")
            .build();

        while (true) {
            RateLimitDecision decision = engine.tryAcquire(context, config);

            if (decision.isAllowed()) {
                // Process the item
                callExternalApi(item);
                break;
            } else {
                // Wait and retry
                long waitMs = decision.getRetryAfterSeconds() * 1000;
                System.out.printf("Rate limited. Waiting %d seconds...%n",
                    decision.getRetryAfterSeconds());

                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting", e);
                }
            }
        }
    }

    private void callExternalApi(Item item) {
        // Make API call
        System.out.println("Processing: " + item.getId());
    }
}
```

### Multi-Tier Rate Limiting

```java
public class MultiTierRateLimiter {

    private final LimiterEngine engine;
    private final List<RateLimitConfig> tiers;

    public MultiTierRateLimiter() {
        StorageProvider storage = new CaffeineStorageProvider();
        KeyResolver keyResolver = context -> context.getKeyExpression();
        this.engine = new LimiterEngine(storage, keyResolver, null, null);

        // Define multiple tiers
        this.tiers = Arrays.asList(
            // Burst protection: 10 requests per second
            RateLimitConfig.builder()
                .name("burst")
                .algorithm(Algorithm.TOKEN_BUCKET)
                .requests(10)
                .window(1)
                .windowUnit(TimeUnit.SECONDS)
                .build(),

            // Minute limit: 100 requests per minute
            RateLimitConfig.builder()
                .name("minute")
                .algorithm(Algorithm.SLIDING_WINDOW)
                .requests(100)
                .window(60)
                .windowUnit(TimeUnit.SECONDS)
                .build(),

            // Hourly limit: 1000 requests per hour
            RateLimitConfig.builder()
                .name("hourly")
                .algorithm(Algorithm.SLIDING_WINDOW)
                .requests(1000)
                .window(3600)
                .windowUnit(TimeUnit.SECONDS)
                .build()
        );
    }

    public RateLimitDecision checkAllTiers(String key) {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression(key)
            .build();

        // Check all tiers, return first denial or last success
        RateLimitDecision lastDecision = null;

        for (RateLimitConfig config : tiers) {
            RateLimitDecision decision = engine.tryAcquire(context, config);
            lastDecision = decision;

            if (!decision.isAllowed()) {
                // Return first tier that denies
                return decision;
            }
        }

        return lastDecision;
    }
}
```

### Distributed Rate Limiting with Redis

```java
public class DistributedRateLimiter {

    private final LimiterEngine engine;
    private final RateLimitConfig config;

    public DistributedRateLimiter(String redisHost, int redisPort) {
        // Configure Jedis pool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);

        JedisPool jedisPool = new JedisPool(poolConfig, redisHost, redisPort);

        // Create tiered storage for resilience
        RedisStorageProvider l1 = new RedisStorageProvider(jedisPool);
        CaffeineStorageProvider l2 = new CaffeineStorageProvider();
        StorageProvider storage = new TieredStorageProvider(l1, l2,
            FailStrategy.FAIL_OPEN);

        KeyResolver keyResolver = context -> context.getKeyExpression();
        this.engine = new LimiterEngine(storage, keyResolver, null, null);

        this.config = RateLimitConfig.builder()
            .name("distributed-limiter")
            .algorithm(Algorithm.TOKEN_BUCKET)
            .requests(10000)
            .window(3600)
            .windowUnit(TimeUnit.SECONDS)
            .build();
    }

    public boolean isAllowed(String apiKey) {
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("api:" + apiKey)
            .build();

        return engine.tryAcquire(context, config).isAllowed();
    }
}
```

---

## Security Features

### Key Validation

Prevent injection attacks in rate limit keys.

```java
import com.lycosoft.ratelimit.security.VariableValidator;

public class SecureKeyResolver implements KeyResolver {

    private final VariableValidator validator = new VariableValidator();

    @Override
    public String resolve(RateLimitContext context) {
        String key = context.getKeyExpression();

        // Validate key doesn't contain dangerous patterns
        if (!validator.isSafeValue(key)) {
            throw new SecurityException("Invalid rate limit key: " + key);
        }

        return key;
    }
}
```

### Sensitive Data Filtering

Mask sensitive data in logs.

```java
import com.lycosoft.ratelimit.audit.SensitiveDataFilter;

public class SecureAuditLogger implements AuditLogger {

    private final SensitiveDataFilter filter = new SensitiveDataFilter();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void logEnforcement(RateLimitDecision decision) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("limiter", decision.getLimiterName());
        logData.put("allowed", decision.isAllowed());
        logData.put("remaining", decision.getRemaining());

        // Filter sensitive data before logging
        Map<String, Object> filtered = filter.filter(logData);
        logger.info("Rate limit decision: {}", filtered);
    }
}
```

---

## Resilience Patterns

### Circuit Breaker

Prevent cascading failures.

```java
import com.lycosoft.ratelimit.resilience.JitteredCircuitBreaker;

public class ResilientRateLimiter {

    private final JitteredCircuitBreaker circuitBreaker;
    private final StorageProvider primaryStorage;
    private final StorageProvider fallbackStorage;

    public ResilientRateLimiter() {
        this.circuitBreaker = new JitteredCircuitBreaker(
            0.5,      // 50% failure threshold
            10_000,   // 10 second window
            30_000,   // 30 second half-open timeout
            0.3,      // ±30% jitter
            1         // max concurrent probes
        );

        this.primaryStorage = new RedisStorageProvider(jedisPool);
        this.fallbackStorage = new CaffeineStorageProvider();
    }

    public boolean tryAcquire(String key, RateLimitConfig config) {
        if (circuitBreaker.isOpen()) {
            // Use fallback storage
            return fallbackStorage.tryAcquire(key, config,
                System.currentTimeMillis());
        }

        try {
            boolean result = primaryStorage.tryAcquire(key, config,
                System.currentTimeMillis());
            circuitBreaker.recordSuccess();
            return result;
        } catch (Exception e) {
            circuitBreaker.recordFailure();

            if (circuitBreaker.isOpen()) {
                // Fallback to local storage
                return fallbackStorage.tryAcquire(key, config,
                    System.currentTimeMillis());
            }

            throw e;
        }
    }
}
```

### Failure Strategies

```java
// FAIL_OPEN: Allow requests when storage fails (availability)
RateLimitConfig failOpen = RateLimitConfig.builder()
    .name("fail-open")
    .requests(100)
    .window(60)
    .windowUnit(TimeUnit.SECONDS)
    .failStrategy(FailStrategy.FAIL_OPEN)
    .build();

// FAIL_CLOSED: Deny requests when storage fails (consistency)
RateLimitConfig failClosed = RateLimitConfig.builder()
    .name("fail-closed")
    .requests(100)
    .window(60)
    .windowUnit(TimeUnit.SECONDS)
    .failStrategy(FailStrategy.FAIL_CLOSED)
    .build();
```

---

## Monitoring

### Custom Metrics Exporter

```java
public class SimpleMetricsExporter implements MetricsExporter {

    private final AtomicLong allowedCount = new AtomicLong();
    private final AtomicLong deniedCount = new AtomicLong();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final AtomicLong requestCount = new AtomicLong();

    @Override
    public void recordAllow(String limiterName, String key) {
        allowedCount.incrementAndGet();
    }

    @Override
    public void recordDeny(String limiterName, String key, long retryAfterMs) {
        deniedCount.incrementAndGet();
    }

    @Override
    public void recordLatency(String limiterName, long latencyNanos) {
        totalLatencyNanos.addAndGet(latencyNanos);
        requestCount.incrementAndGet();
    }

    @Override
    public void recordUsage(String limiterName, double usagePercent) {
        // Store usage metrics
    }

    @Override
    public void recordError(String limiterName, String errorType) {
        // Store error metrics
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("allowed", allowedCount.get());
        metrics.put("denied", deniedCount.get());
        metrics.put("avgLatencyMs", requestCount.get() > 0
            ? (totalLatencyNanos.get() / requestCount.get()) / 1_000_000.0
            : 0);
        return metrics;
    }
}
```

### Storage Health Check

```java
public void healthCheck() {
    StorageProvider storage = ...;

    boolean healthy = storage.isHealthy();
    Map<String, Object> diagnostics = storage.getDiagnostics();

    System.out.println("Storage healthy: " + healthy);
    System.out.println("Diagnostics: " + diagnostics);
}
```

---

## Best Practices

### 1. Choose the Right Algorithm

```java
// For user-facing APIs with burst tolerance
RateLimitConfig.builder()
    .algorithm(Algorithm.TOKEN_BUCKET)
    .capacity(150)  // 50% burst allowance
    .build();

// For strict rate limiting (payments, writes)
RateLimitConfig.builder()
    .algorithm(Algorithm.SLIDING_WINDOW)
    .build();
```

### 2. Use Tiered Storage for Production

```java
// Development: In-memory only
StorageProvider dev = new CaffeineStorageProvider();

// Production: Tiered with failover
StorageProvider prod = new TieredStorageProvider(
    new RedisStorageProvider(jedisPool),
    new CaffeineStorageProvider(),
    FailStrategy.FAIL_OPEN
);
```

### 3. Implement Proper Key Resolution

```java
// Bad: Using user-controlled input directly
String key = userInput;  // Injection risk!

// Good: Validate and sanitize
String key = sanitize(userInput);
if (!validator.isSafeValue(key)) {
    throw new SecurityException("Invalid key");
}
```

### 4. Handle Rate Limit Responses Gracefully

```java
RateLimitDecision decision = engine.tryAcquire(context, config);

if (!decision.isAllowed()) {
    // Log for monitoring
    logger.info("Rate limit exceeded for key: {}", key);

    // Return helpful error
    throw new RateLimitExceededException(
        "Rate limit exceeded. Retry after " +
        decision.getRetryAfterSeconds() + " seconds"
    );
}
```

### 5. Monitor Key Metrics

```java
// Track denial rate
double denialRate = (double) denied / (allowed + denied);
if (denialRate > 0.1) {
    alerting.warn("High denial rate: " + denialRate);
}

// Track latency
if (avgLatencyMs > 10) {
    alerting.warn("High rate limit latency: " + avgLatencyMs + "ms");
}
```

---

## Troubleshooting

### Common Issues

**1. Rate limits not enforced:**
- Check storage provider is healthy
- Verify key resolution is consistent
- Ensure configuration is correct

**2. Too many denials:**
- Review limit values
- Check for key collisions
- Verify time window is appropriate

**3. Storage connection issues:**
- Check Redis connectivity
- Verify pool configuration
- Enable health monitoring

**4. Performance issues:**
- Use in-memory storage for low-latency
- Optimize key resolution
- Consider caching decisions

---

## API Reference

### StorageProvider Interface

```java
public interface StorageProvider {
    long getCurrentTime();
    boolean tryAcquire(String key, RateLimitConfig config, long currentTime);
    void reset(String key);
    Optional<RateLimitState> getState(String key);
    boolean isHealthy();
    Map<String, Object> getDiagnostics();
}
```

### KeyResolver Interface

```java
@FunctionalInterface
public interface KeyResolver {
    String resolve(RateLimitContext context);
}
```

### MetricsExporter Interface

```java
public interface MetricsExporter {
    void recordAllow(String limiterName, String key);
    void recordDeny(String limiterName, String key, long retryAfterMs);
    void recordLatency(String limiterName, long latencyNanos);
    void recordUsage(String limiterName, double usagePercent);
    void recordError(String limiterName, String errorType);
}
```

---

**Documentation Version:** 1.1.0
