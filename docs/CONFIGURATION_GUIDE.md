# Advanced Features Configuration Guide

Complete configuration reference for the Rate Limiting Library v1.1.0 with advanced networking features.

---

## 📋 **Table of Contents**

1. [Spring Boot Configuration](#spring-boot-configuration)
2. [Quarkus Configuration](#quarkus-configuration)
3. [Trusted Proxy Resolution](#trusted-proxy-resolution)
4. [Adaptive Throttling](#adaptive-throttling)
5. [RFC 9457 Problem Details](#rfc-9457-problem-details)
6. [Complete Examples](#complete-examples)
7. [Production Deployment](#production-deployment)

---

## 🌱 **Spring Boot Configuration**

### Basic Configuration

```yaml
# application.yml
ratelimit:
  enabled: true
  
  # SpEL compilation for 40× performance
  spel:
    compiler-mode: IMMEDIATE    # IMMEDIATE, MIXED, or OFF
    cache-size: 1000            # Expression cache size
  
  # Trusted proxy resolution
  proxy:
    trusted-hops: 2             # Count from right in X-Forwarded-For
    trusted-proxies:
      - "10.0.0.0/8"           # Internal network
      - "104.16.0.0/12"        # Cloudflare IPv4
      - "2606:4700::/32"       # Cloudflare IPv6
      - "172.16.0.0/12"        # Docker network
  
  # Adaptive throttling (soft limits)
  throttling:
    enabled: true
    soft-limit: 80              # Start throttling at 80%
    max-delay-ms: 2000          # Maximum 2 second delay
    strategy: LINEAR            # LINEAR or EXPONENTIAL
```

### Advanced Spring Configuration

```yaml
ratelimit:
  enabled: true
  
  spel:
    compiler-mode: IMMEDIATE
    cache-size: 2000
  
  proxy:
    trusted-hops: 3             # For multi-layer proxies
    trusted-proxies:
      # Cloudflare
      - "103.21.244.0/22"
      - "103.22.200.0/22"
      - "103.31.4.0/22"
      - "104.16.0.0/13"
      # AWS ALB
      - "35.156.0.0/14"
      - "52.16.0.0/15"
      # Internal
      - "10.0.0.0/8"
  
  throttling:
    enabled: true
    soft-limit: 85              # More aggressive
    max-delay-ms: 3000          # Higher max delay
    strategy: EXPONENTIAL        # Ramps up faster
```

---

## 🔥 **Quarkus Configuration**

### Basic Configuration

```properties
# application.properties
ratelimit.enabled=true

# SpEL compilation
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=1000

# Trusted proxy resolution
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=10.0.0.0/8,104.16.0.0/12,2606:4700::/32

# Adaptive throttling
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=80
ratelimit.throttling.max-delay-ms=2000
ratelimit.throttling.strategy=LINEAR
```

### Advanced Quarkus Configuration

```properties
ratelimit.enabled=true

# SpEL optimization
ratelimit.spel.compiler-mode=IMMEDIATE
ratelimit.spel.cache-size=2000

# Multi-layer proxy setup
ratelimit.proxy.trusted-hops=3
ratelimit.proxy.trusted-proxies=103.21.244.0/22,103.22.200.0/22,104.16.0.0/13,10.0.0.0/8

# Aggressive throttling
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=85
ratelimit.throttling.max-delay-ms=3000
ratelimit.throttling.strategy=EXPONENTIAL

# Micrometer metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.registry-enabled-default=true
quarkus.micrometer.export.prometheus.enabled=true
```

---

## 🔒 **Trusted Proxy Resolution**

### Understanding Hop Counting

**Problem**: Blindly trusting X-Forwarded-For allows IP spoofing.

**Solution**: Count backwards from the right by the number of trusted hops.

### Example Scenarios

#### Scenario 1: Single Proxy (Nginx)

```
Architecture: Client → Nginx → Application

X-Forwarded-For: client_ip
Remote Address: nginx_ip (10.0.1.5)

Configuration:
ratelimit.proxy.trusted-hops=1
ratelimit.proxy.trusted-proxies=10.0.0.0/8

Resolution:
1. nginx_ip matches 10.0.0.0/8 ✓
2. Count 1 hop from right
3. Result: client_ip
```

#### Scenario 2: Cloudflare + Nginx

```
Architecture: Client → Cloudflare → Nginx → Application

X-Forwarded-For: client_ip, cloudflare_ip
Remote Address: nginx_ip (10.0.1.5)

Configuration:
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=10.0.0.0/8

Resolution:
1. nginx_ip matches 10.0.0.0/8 ✓
2. XFF: [client_ip, cloudflare_ip]
3. Count 2 hops from right: client_ip
4. Result: client_ip (trusted Cloudflare's determination)
```

#### Scenario 3: Multi-Layer Cloud

```
Architecture: Client → Cloudflare → AWS ALB → Nginx → Application

X-Forwarded-For: client_ip, cloudflare_ip, alb_ip
Remote Address: nginx_ip (10.0.1.5)

Configuration:
ratelimit.proxy.trusted-hops=3
ratelimit.proxy.trusted-proxies=10.0.0.0/8

Resolution:
1. nginx_ip matches 10.0.0.0/8 ✓
2. XFF: [client_ip, cloudflare_ip, alb_ip]
3. Count 3 hops from right: client_ip
4. Result: client_ip
```

### Security Warning

⚠️ **CRITICAL**: Hop counting only works if you know exactly how many proxies are in your control.

**Example of Misconfiguration**:
```
Configuration: trusted-hops=1 (expecting 1 proxy)
Reality: 2 proxies (Cloudflare + Nginx)

X-Forwarded-For: malicious_ip, real_client_ip, cloudflare_ip
Remote: nginx_ip

Resolution:
- Count 1 hop from right: cloudflare_ip (WRONG!)
- Should be: trusted-hops=2 → real_client_ip
```

### Common CIDR Ranges

```yaml
# Cloudflare IPv4
- "103.21.244.0/22"
- "103.22.200.0/22"
- "103.31.4.0/22"
- "104.16.0.0/13"
- "104.24.0.0/14"
- "108.162.192.0/18"

# Cloudflare IPv6
- "2400:cb00::/32"
- "2606:4700::/32"
- "2803:f800::/32"
- "2405:b500::/32"

# AWS ALB (example regions)
- "35.156.0.0/14"
- "52.16.0.0/15"
- "18.130.0.0/16"

# Internal Networks
- "10.0.0.0/8"
- "172.16.0.0/12"
- "192.168.0.0/16"

# Localhost
- "127.0.0.0/8"
- "::1/128"
```

---

## ⚡ **Adaptive Throttling**

### How It Works

```
Below soft limit (0-80%):     No delay (full speed)
Between soft-hard (80-100%):  Gradual delay increase
Above hard limit (100%+):     Rejected with 429
```

### Formula

```
delay = ((usage - softLimit) / (hardLimit - softLimit)) × maxDelay
```

### Example Calculations

**Configuration**: softLimit=80, hardLimit=100, maxDelay=2000ms

| Usage | Linear Delay | Exponential Delay |
|-------|-------------|-------------------|
| 50% | 0ms | 0ms |
| 80% | 0ms | 0ms |
| 85% | 500ms | 125ms |
| 90% | 1000ms | 400ms |
| 95% | 1500ms | 900ms |
| 100% | 2000ms | 2000ms |
| 101% | REJECTED | REJECTED |

### Strategy Comparison

**LINEAR** (default):
```java
delay = ratio × maxDelay
// Smooth, predictable increase
// Better for user-facing APIs
```

**EXPONENTIAL**:
```java
delay = ratio² × maxDelay
// Gentle early, steep late
// Better for rate-sensitive operations
```

### Thread Safety Warning

⚠️ **WARNING**: Adaptive throttling uses `Thread.sleep()` which can exhaust thread pools.

**Solutions**:

**Spring WebFlux** (Reactive):
```java
// Instead of Thread.sleep()
return Mono.delay(Duration.ofMillis(delayMs))
    .then(Mono.fromCallable(() -> service.process()));
```

**Quarkus Mutiny** (Reactive):
```java
// Instead of Thread.sleep()
return Uni.createFrom().item(request)
    .onItem().delayIt().by(Duration.ofMillis(delayMs))
    .onItem().transform(service::process);
```

**Production Recommendation**: Use adaptive throttling with reactive frameworks only.

---

## 📄 **RFC 9457 Problem Details**

### Automatic Response Format

When rate limits are exceeded, the library automatically returns:

**HTTP Response**:
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
  "rate_limit_policy": "100;w=60"
}
```

### Client Integration

**JavaScript/TypeScript**:
```typescript
async function callApi(url: string): Promise<any> {
  const response = await fetch(url);
  
  if (response.status === 429) {
    const problem = await response.json();
    console.warn(`Rate limited: ${problem.detail}`);
    
    // Automatic retry after delay
    await new Promise(resolve => 
      setTimeout(resolve, problem.retry_after * 1000)
    );
    
    return callApi(url); // Retry
  }
  
  return response.json();
}
```

**Java (Spring RestTemplate)**:
```java
try {
    return restTemplate.getForObject(url, ResponseType.class);
} catch (HttpClientErrorException.TooManyRequests ex) {
    ProblemDetail problem = objectMapper.readValue(
        ex.getResponseBodyAsString(), 
        ProblemDetail.class
    );
    
    Thread.sleep(problem.getRetryAfter() * 1000);
    return restTemplate.getForObject(url, ResponseType.class);
}
```

**Python (Requests)**:
```python
import requests
import time

def call_api(url):
    response = requests.get(url)
    
    if response.status_code == 429:
        problem = response.json()
        retry_after = problem['retry_after']
        print(f"Rate limited. Retrying in {retry_after}s...")
        time.sleep(retry_after)
        return call_api(url)
    
    return response.json()
```

---

## 🚀 **Complete Examples**

### Example 1: Public API (Cloudflare + Nginx)

**Architecture**:
```
Internet → Cloudflare → Nginx (10.0.1.5) → Spring Boot App
```

**Configuration**:
```yaml
ratelimit:
  enabled: true
  
  spel:
    compiler-mode: IMMEDIATE
    cache-size: 1000
  
  proxy:
    trusted-hops: 2  # Cloudflare + Nginx
    trusted-proxies:
      - "104.16.0.0/12"    # Cloudflare
      - "10.0.0.0/8"       # Internal
  
  throttling:
    enabled: true
    soft-limit: 80
    max-delay-ms: 2000
    strategy: LINEAR
```

**Controller**:
```java
@RestController
@RequestMapping("/api")
public class ApiController {
    
    @RateLimit(
        key = "#ip",                // Per-IP limiting
        requests = 1000,
        window = 3600
    )
    @GetMapping("/public/search")
    public SearchResults search(@RequestParam String q) {
        return searchService.search(q);
    }
}
```

### Example 2: Internal API (AWS ALB + Nginx)

**Architecture**:
```
VPC → AWS ALB → Nginx (10.0.1.5) → Quarkus App
```

**Configuration**:
```properties
ratelimit.enabled=true
ratelimit.spel.compiler-mode=IMMEDIATE

# AWS ALB + Nginx
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=35.156.0.0/14,10.0.0.0/8

# Aggressive throttling
ratelimit.throttling.enabled=true
ratelimit.throttling.soft-limit=85
ratelimit.throttling.max-delay-ms=3000
ratelimit.throttling.strategy=EXPONENTIAL
```

**Resource**:
```java
@Path("/api")
public class ApiResource {
    
    @RateLimit(
        key = "#securityIdentity.principal.name",
        requests = 100,
        window = 60
    )
    @POST
    @Path("/orders")
    public Order createOrder(OrderRequest request) {
        return orderService.create(request);
    }
}
```

### Example 3: Multi-Tenant SaaS

**Architecture**:
```
Client → Cloudflare → AWS ALB → K8s Ingress → Spring Boot
```

**Configuration**:
```yaml
ratelimit:
  enabled: true
  
  proxy:
    trusted-hops: 3  # Cloudflare + ALB + Ingress
    trusted-proxies:
      - "104.16.0.0/12"
      - "35.156.0.0/14"
      - "10.0.0.0/8"
  
  throttling:
    enabled: true
    soft-limit: 80
    max-delay-ms: 2000
    strategy: LINEAR
```

**Multi-Tenant Limiting**:
```java
@RestController
public class TenantController {
    
    @RateLimits({
        @RateLimit(
            name = "per-tenant",
            key = "#tenant.id",
            requests = 10000,
            window = 3600
        ),
        @RateLimit(
            name = "per-user",
            key = "#tenant.id + ':' + #user.id",
            requests = 100,
            window = 60
        )
    })
    @PostMapping("/api/actions")
    public Result performAction(
        @RequestHeader("X-Tenant-ID") Tenant tenant,
        @AuthenticationPrincipal User user,
        @RequestBody ActionRequest request
    ) {
        return actionService.execute(request);
    }
}
```

---

## 🏭 **Production Deployment**

### Small Deployment (1-5 nodes)

```yaml
# Use in-memory storage
storage:
  type: IN_MEMORY

proxy:
  trusted-hops: 1
  trusted-proxies: [127.0.0.0/8]

throttling:
  enabled: false  # Low traffic
```

### Medium Deployment (5-50 nodes)

```yaml
# Use Redis for consistency
storage:
  type: REDIS
  redis:
    host: redis.internal
    port: 6379

proxy:
  trusted-hops: 2
  trusted-proxies:
    - "104.16.0.0/12"
    - "10.0.0.0/8"

throttling:
  enabled: true
  soft-limit: 80
  max-delay-ms: 2000
```

### Large Deployment (50+ nodes)

```yaml
# Use tiered storage
storage:
  type: TIERED
  l1:
    type: REDIS
    redis:
      host: redis-cluster.internal
      port: 6379
  l2:
    type: CAFFEINE

proxy:
  trusted-hops: 3
  trusted-proxies:
    - "104.16.0.0/12"
    - "35.156.0.0/14"
    - "10.0.0.0/8"

throttling:
  enabled: true
  soft-limit: 85
  max-delay-ms: 3000
  strategy: EXPONENTIAL
```

### Security Checklist

- [ ] Configure correct number of trusted hops
- [ ] Add all proxy CIDRs to trusted-proxies
- [ ] Test IP spoofing protection
- [ ] Enable SpEL compilation (40× faster)
- [ ] Configure adaptive throttling appropriately
- [ ] Enable metrics export (Prometheus)
- [ ] Set up audit logging
- [ ] Configure circuit breaker thresholds
- [ ] Test L1/L2 failover
- [ ] Review thread pool sizes (if using adaptive throttling)

---

## 📊 **Monitoring**

### Prometheus Metrics

```prometheus
# Rate limiting
rate(ratelimit_requests_total{result="allowed"}[5m])
rate(ratelimit_requests_total{result="denied"}[5m])

# Adaptive throttling
histogram_quantile(0.95, ratelimit_delay_seconds_bucket)

# Circuit breaker
ratelimit_circuit_state{state="open"}
```

### Grafana Dashboard

```json
{
  "title": "Rate Limiting",
  "panels": [
    {
      "title": "Request Rate",
      "targets": [
        "rate(ratelimit_requests_total[5m])"
      ]
    },
    {
      "title": "Denial Rate",
      "targets": [
        "rate(ratelimit_requests_total{result='denied'}[5m])"
      ]
    }
  ]
}
```

---

## 🎓 **Best Practices**

1. **Always verify hop count** in staging before production
2. **Start with liberal thresholds** and tighten based on metrics
3. **Use adaptive throttling judiciously** (thread pool considerations)
4. **Enable SpEL compilation** for 40× performance
5. **Monitor denial rates** to detect attacks
6. **Test IP spoofing protection** with synthetic requests
7. **Use tiered storage** for resilience in production
8. **Configure appropriate soft limits** (80-85% typical)
9. **Choose throttling strategy** based on traffic patterns
10. **Enable comprehensive metrics** for observability

---

**Configuration Guide Complete** ✅

For more information, see:
- [ADVANCED_FEATURES_COMPLETE.md](ADVANCED_FEATURES_COMPLETE.md)
- [README.md](../README.md)
- [rate-limiter-implementation-guide.md](rate-limiter-implementation-guide.md)
