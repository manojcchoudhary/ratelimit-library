# Rate Limiting Library

A production-grade, high-performance rate limiting library for Java applications with support for Spring Boot and Quarkus.

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Build](https://github.com/manojcchoudhary/ratelimit-library/actions/workflows/build-badge.yml/badge.svg)

## 🚀 Features

### Core Capabilities
- **Multiple Algorithms**: Token Bucket (burst handling) and Sliding Window (accuracy)
- **Framework Agnostic**: Core module has zero dependencies
- **Spring Boot Ready**: Zero-configuration annotation-driven integration
- **Quarkus Ready**: CDI interceptor support with Vert.x integration
- **Distributed Storage**: Redis support with atomic Lua scripts
- **High Performance**: O(1) algorithms, <1μs local overhead, <100μs distributed overhead
- **Production Resilient**: Circuit breaker, L1/L2 tiered storage, automatic failover

### Advanced Features
- **40× SpEL Performance**: Compiled bytecode with expression caching
- **Security Hardened**: SpEL injection prevention, ClassLoader blocking, PII protection
- **Observability**: Prometheus metrics, async audit logging, circuit state monitoring
- **CAP-Aware**: Configurable CP/AP mode switching on L1 failure
- **Thundering Herd Prevention**: Jittered circuit breaker recovery
- **Lua Script Versioning**: SHA-1 verification with automatic reload

## 📦 Modules

| Module | Description | Size |
|--------|-------------|------|
| `rl-core` | Core algorithms, SPIs, security, resilience | ~10,800 LOC |
| `rl-spi-redis` | Redis storage with versioned Lua scripts | ~770 LOC |
| `rl-spi-caffeine` | High-performance in-memory storage | ~800 LOC |
| `rl-adapter-spring` | Spring Boot integration with AOP | ~2,760 LOC |
| `rl-adapter-quarkus` | Quarkus CDI integration | ~800 LOC |

**Total**: ~15,900 lines of production code

## 🏃 Quick Start

### Spring Boot

**1. Add Dependency**:
```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-adapter-spring</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

**2. Use the Annotation**:
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

That's it! The library auto-configures itself with sensible defaults.

### Quarkus

**1. Add Dependency**:
```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-adapter-quarkus</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

**2. Use the Annotation**:
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

## 📚 Usage Examples

### Basic Rate Limiting

**Per-IP Rate Limit**:
```java
@RateLimit(
    key = "#ip",
    requests = 1000,
    window = 3600,
    windowUnit = TimeUnit.SECONDS
)
@GetMapping("/public/search")
public SearchResults search(@RequestParam String q) {
    return searchService.search(q);
}
```

**Per-User Rate Limit** (SpEL):
```java
@RateLimit(
    key = "#user.id",
    requests = 100,
    window = 60
)
@PostMapping("/api/orders")
public Order createOrder(@AuthenticationPrincipal User user,
                        @RequestBody OrderRequest request) {
    return orderService.create(request);
}
```

### Tiered Limiting

Apply multiple limits to a single endpoint:

```java
@RateLimits({
    @RateLimit(name = "burst", requests = 10, window = 1),
    @RateLimit(name = "hourly", requests = 1000, window = 3600)
})
@GetMapping("/api/resource")
public Resource getResource() {
    return resourceService.fetch();
}
```

### Custom Keys

**Composite Keys**:
```java
@RateLimit(
    key = "#tenant.id + ':' + #user.id",
    requests = 50,
    window = 60
)
```

**Request Body Field**:
```java
@RateLimit(
    key = "#request.customerId",
    requests = 20,
    window = 60
)
@PostMapping("/api/process")
public Result process(@RequestBody ProcessRequest request) {
    return processor.process(request);
}
```

## 📖 Documentation

Comprehensive developer documentation is available for each use case:

| Guide | Description |
|-------|-------------|
| [Developer Guide](docs/DEVELOPER_GUIDE.md) | Complete unified documentation |
| [Simple Project Guide](docs/simple-project/README.md) | Standalone Java without frameworks |
| [Spring Boot Guide](docs/spring-boot/README.md) | Spring Boot with annotations & YAML |
| [Quarkus Guide](docs/quarkus/README.md) | Quarkus with CDI & reactive support |

Each guide covers:
- Rate limiting algorithms (Token Bucket, Sliding Window)
- Complete YAML/Properties configuration reference
- Storage backends (Caffeine, Redis, Tiered)
- Security features (SpEL injection prevention, IP spoofing protection)
- Resilience patterns (Circuit Breaker, Failover)
- Monitoring & observability (Prometheus metrics)
- Production deployment recommendations

## ⚙️ Configuration

### Spring Boot (application.yml)

```yaml
ratelimit:
  enabled: true
  spel:
    compiler-mode: IMMEDIATE  # IMMEDIATE, MIXED, OFF
    cache-size: 1000
```

### Storage Configuration

**Tiered Storage** (L1/L2 with failover):
```java
@Bean
public StorageProvider storageProvider(JedisPool jedisPool) {
    RedisStorageProvider l1 = new RedisStorageProvider(jedisPool);
    CaffeineStorageProvider l2 = new CaffeineStorageProvider();
    
    return new TieredStorageProvider(
        l1,                              // L1: Distributed (CP mode)
        l2,                              // L2: Local (AP mode)
        RateLimitConfig.FailStrategy.FAIL_OPEN  // Prioritize availability
    );
}
```

## 📈 Performance

For detailed benchmark results, see [BENCHMARK.md](BENCHMARK.md).

**Key Performance Metrics:**
- In-memory storage: ~30M ops/sec, ~33ns average latency
- Caffeine storage: ~3.3M ops/sec, ~300ns average latency
- Redis storage: ~19K ops/sec, ~55μs average latency
- Tiered storage (Redis + Caffeine): Automatic failover with minimal overhead
- SpEL compilation: 40× faster than interpreted mode (29 ops/μs vs 1.5 ops/μs)

Run benchmarks yourself:
```bash
mvn clean package -pl rl-benchmarks -am -DskipTests
java -jar rl-benchmarks/target/benchmarks.jar
```

## 📊 Monitoring

### Prometheus Metrics

```prometheus
# Rate calculations
rate(ratelimit_requests_total{result="denied"}[5m])   # Denial rate
sum(rate(ratelimit_requests_total{result="allowed"}[5m]))  # Allowed rate
```

## 📄 License

This project is licensed under the Apache License 2.0.

---

**Built with ❤️ for high-performance, production-grade rate limiting**
