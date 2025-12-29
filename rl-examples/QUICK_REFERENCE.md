# Examples Module - Quick Reference

## 🚀 **Quick Start (5 Minutes)**

### 1. Start Infrastructure
```bash
cd rl-examples/docker
docker-compose up -d
```

### 2. Run Spring Boot Example
```bash
cd rl-examples/rl-example-spring-boot
mvn spring-boot:run
```

### 3. Test Rate Limiting
```bash
# Test IP-based limiting (10/minute)
for i in {1..12}; do
  curl http://localhost:8080/public/search?q=test
done
# First 10 succeed, last 2 get 429
```

### 4. View Metrics
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Metrics**: http://localhost:8080/actuator/prometheus

---

## 📚 **Examples Overview**

| Example | Framework | Algorithm | Key | Scenario |
|---------|-----------|-----------|-----|----------|
| **Public API** | Spring Boot | Fixed Window | IP | Protect public endpoints |
| **User Tiers** | Spring Boot | Sliding Window | User + Tier | Different limits per subscription |
| **Partner API** | Spring Boot | Token Bucket | Partner ID | Burst capacity for partners |
| **Reactive** | WebFlux | Fixed Window | IP | Non-blocking rate limiting |
| **Standalone** | Pure Java | Token Bucket | Task ID | Direct engine usage |

---

## 🎯 **Use Case Selector**

### Scenario: Public API Protection
```java
@RateLimit(
    key = "#ip",
    requests = 10,
    window = 60
)
@GetMapping("/public/search")
public Response search(@RequestParam String q) { ... }
```

### Scenario: Different Limits Per User Tier
```java
@RateLimit(
    key = "#user.username + ':' + #user.attributes['tier']",
    requests = 10,  // Multiplied by tier
    window = 60,
    algorithm = "SLIDING_WINDOW"
)
@GetMapping("/user/dashboard")
public Response getDashboard(@AuthenticationPrincipal UserDetails user) { ... }
```

### Scenario: Burst-Friendly Partner API
```java
@RateLimit(
    key = "#headers['X-Partner-ID']",
    algorithm = "TOKEN_BUCKET",
    capacity = 100,      // Can burst to 100
    refillRate = 10      // Refills 10/second
)
@GetMapping("/partner/data")
public Response getData() { ... }
```

### Scenario: Multiple Limits (Burst + Sustained)
```java
@RateLimits({
    @RateLimit(name = "burst", requests = 5, window = 10),
    @RateLimit(name = "sustained", requests = 100, window = 3600)
})
@GetMapping("/api/data")
public Response getData() { ... }
```

---

## ⚙️ **Configuration Strategies**

### Strategy 1: Simple (In-Memory)
**Best for**: Single node, development

```yaml
# No configuration needed - uses defaults
ratelimit:
  enabled: true
```

### Strategy 2: Distributed (Redis)
**Best for**: Kubernetes, microservices

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

ratelimit:
  storage:
    type: REDIS
```

### Strategy 3: Resilient (Fail-Open)
**Best for**: High availability

```yaml
ratelimit:
  storage:
    type: TIERED
    l1:
      type: REDIS
      fail-strategy: FAIL_OPEN  # Keep app alive
    l2:
      type: CAFFEINE
```

---

## 🔍 **Key Resolution Examples**

| Expression | Resolves To | Use Case |
|------------|-------------|----------|
| `#ip` | Client IP | Public APIs |
| `#user.username` | Authenticated user | User quotas |
| `#headers['X-API-Key']` | API key header | Partner APIs |
| `#args[0].customerId` | Method argument | Business logic |
| `#tenant + ':' + #user` | Multi-tenant | SaaS applications |

---

## 📊 **Algorithm Comparison**

### Fixed Window
```
Time:    [0---------60s---------120s]
Limit:   [10 requests][10 requests]
Burst:   Can use all 10 at once
Reset:   Hard reset at 60s mark

Use: Simple protection
```

### Sliding Window
```
Time:    Continuous sliding 60s window
Limit:   10 requests in any 60s period
Burst:   Smooth distribution
Reset:   Gradual

Use: Accurate user quotas
```

### Token Bucket
```
Capacity: 100 tokens
Refill:   10 tokens/second
Burst:    Can use all 100 immediately
Steady:   Limited to 10/second after burst

Use: Bursty workloads
```

---

## 🧪 **Testing Commands**

### Test IP-Based Limiting
```bash
# Send 15 requests (limit: 10/minute)
for i in {1..15}; do
  curl -s http://localhost:8080/public/search?q=test | jq
  sleep 0.1
done
```

### Test Token Bucket Burst
```bash
# Burst: 100 requests succeed
time for i in {1..100}; do
  curl -s -H "X-Partner-ID: partner-123" \
    http://localhost:8080/partner/data > /dev/null
done

# Should complete in ~1 second (all tokens available)
```

### Test Multi-Tier Limits
```bash
# Burst + Sustained limits
for i in {1..10}; do
  curl -s http://localhost:8080/public/data | jq '.message'
done
# First 5 succeed (burst), next 5 rate limited
```

### Test Fail-Open (Redis Down)
```bash
# Stop Redis
docker-compose stop redis

# Requests still succeed (falls back to L2)
curl http://localhost:8080/public/search?q=test

# Check logs for "L1 failure, using L2"
```

---

## 📈 **Monitoring Queries**

### Prometheus Queries

**Request Rate**:
```promql
rate(ratelimit_requests_total[5m])
```

**Denial Rate**:
```promql
rate(ratelimit_requests_total{result="denied"}[5m])
```

**P95 Latency**:
```promql
histogram_quantile(0.95, rate(ratelimit_latency_seconds_bucket[5m]))
```

**Bucket Usage**:
```promql
(ratelimit_usage_current / ratelimit_usage_limit) * 100
```

**Circuit Breaker State**:
```promql
ratelimit_circuit_state{state="open"}
```

---

## 🐛 **Troubleshooting**

### Issue: All Requests Rate Limited

**Check**:
```bash
# Is Redis running?
docker-compose ps redis

# Check rate limit headers
curl -I http://localhost:8080/public/search?q=test
# Should see: X-RateLimit-Limit, X-RateLimit-Remaining
```

**Fix**:
```yaml
# Increase limits in application.yml
ratelimit:
  defaults:
    requests: 100  # Increase from 10
```

### Issue: Different Users Share Limits

**Check**:
```bash
# Verify key resolution
curl http://localhost:8080/actuator/metrics/ratelimit.keys
```

**Fix**:
```java
// Key must include user identifier
@RateLimit(key = "#user.username")  // ✓ Unique per user
@RateLimit(key = "#ip")              // ✗ Shared across users
```

### Issue: Metrics Not Showing in Grafana

**Check**:
```bash
# Is Prometheus scraping?
curl http://localhost:9090/api/v1/targets
# Should show: spring-boot endpoint UP

# Are metrics exported?
curl http://localhost:8080/actuator/prometheus | grep ratelimit
```

**Fix**:
```yaml
# Enable Prometheus endpoint
management:
  endpoints:
    web:
      exposure:
        include: prometheus,metrics
```

---

## 📝 **Common Patterns**

### Pattern: Gradual Throttling
```java
@RateLimits({
    @RateLimit(name = "immediate", requests = 5, window = 1),
    @RateLimit(name = "short", requests = 50, window = 60),
    @RateLimit(name = "long", requests = 1000, window = 3600)
})
```

### Pattern: Tenant + User Isolation
```java
@RateLimit(
    key = "#tenant.id + ':' + #user.id",
    requests = 100,
    window = 3600
)
```

### Pattern: Conditional Limiting
```java
@RateLimit(
    key = "#user.isPremium() ? 'premium:' + #user.id : 'basic:' + #user.id",
    requests = "#user.isPremium() ? 1000 : 100"
)
```

### Pattern: Header-Based Partner Limits
```java
@RateLimit(
    key = "#headers['X-Partner-ID'] ?: #ip",  // Fallback to IP
    algorithm = "TOKEN_BUCKET",
    capacity = 100
)
```

---

## 🎓 **Learning Path**

1. **Start Simple**: Run Spring Boot example with in-memory storage
2. **Test Scenarios**: Try all 3 examples (IP, User, Partner)
3. **Add Redis**: Switch to redis profile, observe distributed limits
4. **Break Redis**: Test fail-open mode, check L2 fallback
5. **Monitor**: Set up Grafana, create custom dashboards
6. **Customize**: Create your own rate limit patterns

---

## 📚 **Additional Resources**

- **Full Spec**: `EXAMPLES_SPEC.md` - Complete code for all examples
- **Main Docs**: `../README.md` - Library overview
- **Config Guide**: `../CONFIGURATION_GUIDE.md` - All config options
- **IP Resolution**: `../IP_RESOLUTION_GUIDE.md` - IP extraction guide

---

## ⚡ **One-Liner Examples**

```bash
# Quick test IP limiting
curl -w "\nStatus: %{http_code}\n" http://localhost:8080/public/search?q=test

# Load test burst capacity
ab -n 1000 -c 10 -H "X-Partner-ID: test" http://localhost:8080/partner/data

# Monitor in real-time
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/ratelimit.requests | jq'

# Check Redis keys
docker exec -it ratelimit-redis redis-cli KEYS "ratelimit:*"
```

---

**Examples Quick Reference Complete!** 🎉

For complete code and detailed explanations, see `EXAMPLES_SPEC.md`
