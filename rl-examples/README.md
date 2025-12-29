# Rate Limit Library - Examples

This module provides comprehensive examples demonstrating how to integrate and use the Rate Limit library across different frameworks and scenarios.

## 📦 **Module Structure**

```
rl-examples/
├── rl-example-spring-boot/    # Spring Boot 3.x (AOP/Web MVC)
├── rl-example-webflux/         # Reactive Spring (Netty) [TODO]
├── rl-example-standalone/      # Standard Java (No Framework) [TODO]
└── docker/                     # Infrastructure (Redis, Prometheus, Grafana)
```

---

## 🚀 **Quick Start**

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker & Docker Compose (for infrastructure)

### Running the Examples

**1. Start Infrastructure**:
```bash
cd rl-examples/docker
docker-compose up -d

# Verify services
docker-compose ps
```

**2. Run Spring Boot Example**:
```bash
cd rl-examples/rl-example-spring-boot

# With in-memory storage (default)
mvn spring-boot:run

# With Redis (distributed)
mvn spring-boot:run -Dspring.profiles.active=redis

# With resilient fail-open
mvn spring-boot:run -Dspring.profiles.active=resilient
```

**3. Access Services**:
- Application: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- Metrics: http://localhost:8080/actuator/prometheus

---

## 📚 **Spring Boot Example**

### Scenario 1: IP-Based Fixed Window (Public Access)

**Goal**: Protect public endpoints from abuse using client IP address.

**Endpoint**: `GET /api/public/data`

**Rate Limit**:
- 10 requests per 60 seconds per IP
- Fixed window algorithm
- No authentication required

**Testing**:
```bash
# Normal request (will succeed first 10 times)
curl http://localhost:8080/api/public/data

# After 10 requests
HTTP/1.1 429 Too Many Requests
Retry-After: 45
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
```

**Code**:
```java
@RateLimit(
    name = "public-api",
    key = "#ip",           // Client IP
    requests = 10,
    window = 60
)
@GetMapping("/api/public/data")
public Map<String, Object> getPublicData() {
    return Map.of("message", "Public data");
}
```

---

### Scenario 2: User-Tiered Sliding Window (Security Integration)

**Goal**: Apply different rate limits based on user subscription tier.

**Endpoints**:
- `GET /api/user/profile`
- `GET /api/user/dashboard`

**Rate Limits**:
- BASIC users: 100 requests/hour (sliding window)
- PREMIUM users: 1000 requests/hour (sliding window)

**Test Users**:
- Username: `basic` / Password: `password` / Tier: BASIC
- Username: `premium` / Password: `password` / Tier: PREMIUM

**Testing**:
```bash
# BASIC user (100 req/hour limit)
curl -u basic:password http://localhost:8080/api/user/profile

# PREMIUM user (1000 req/hour limit)
curl -u premium:password http://localhost:8080/api/user/dashboard

# Check headers
curl -v -u basic:password http://localhost:8080/api/user/profile
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
```

**Code**:
```java
@RateLimits({
    @RateLimit(
        name = "user-profile-basic",
        algorithm = SLIDING_WINDOW,
        key = "'user:' + #principal.username + ':BASIC'",
        requests = 100,
        window = 3600
    ),
    @RateLimit(
        name = "user-profile-premium",
        algorithm = SLIDING_WINDOW,
        key = "'user:' + #principal.username + ':PREMIUM'",
        requests = 1000,
        window = 3600
    )
})
@GetMapping("/api/user/profile")
public Map<String, Object> getUserProfile(@AuthenticationPrincipal UserDetails user) {
    return Map.of("username", user.getUsername());
}
```

---

### Scenario 3: Token Bucket for API Partners

**Goal**: Allow burst traffic for partner integrations while maintaining average rate limits.

**Endpoint**: `POST /api/partner/events`

**Rate Limit**:
- Algorithm: Token Bucket
- Capacity: 100 tokens (burst)
- Refill Rate: 10 tokens/second (600/minute sustained)

**Testing**:
```bash
# Single request (consumes 1 token)
curl -X POST -H "X-Partner-ID: partner-123" \
  -H "Content-Type: application/json" \
  -d '{"event":"test"}' \
  http://localhost:8080/api/partner/events

# Burst 100 requests (empties bucket)
for i in {1..100}; do
  curl -X POST -H "X-Partner-ID: partner-123" \
    -H "Content-Type: application/json" \
    -d "{\"event\":\"burst-$i\"}" \
    http://localhost:8080/api/partner/events
done

# 101st request fails
HTTP/1.1 429 Too Many Requests

# Wait 10 seconds (100 tokens refilled)
sleep 10
curl -X POST -H "X-Partner-ID: partner-123" \
  -d '{"event":"after-wait"}' \
  http://localhost:8080/api/partner/events
# Success!
```

**Code**:
```java
@RateLimit(
    name = "partner-events",
    algorithm = TOKEN_BUCKET,
    key = "#headers['X-Partner-ID']",
    capacity = 100,       // Max burst
    refillRate = 10       // Tokens/second
)
@PostMapping("/api/partner/events")
public Map<String, Object> receiveEvent(@RequestBody Map<String, Object> event) {
    return Map.of("message", "Event received");
}
```

---

## 🔧 **Configuration Strategies**

### Strategy 1: Simple Starter (In-Memory)

**Use Case**: Single-node apps, local testing

**Configuration**:
```yaml
# Default profile (no configuration needed)
ratelimit:
  enabled: true
  # Uses in-memory storage by default
```

**Running**:
```bash
mvn spring-boot:run
```

**Characteristics**:
- ✅ Zero configuration
- ✅ Fast (no network calls)
- ✅ Perfect for development
- ❌ Not shared across instances
- ❌ Lost on restart

---

### Strategy 2: Scale-Out (Distributed Redis)

**Use Case**: Kubernetes, microservices, production

**Configuration**:
```yaml
# Profile: redis
spring:
  data:
    redis:
      host: localhost
      port: 6379

ratelimit:
  storage:
    type: REDIS
```

**Running**:
```bash
# Start Redis
cd docker && docker-compose up -d redis

# Run with Redis profile
mvn spring-boot:run -Dspring.profiles.active=redis
```

**Characteristics**:
- ✅ Shared state across instances
- ✅ Atomic operations (Lua scripts)
- ✅ Clock sync via Redis TIME
- ✅ Production-grade
- ❌ Requires Redis infrastructure
- ❌ Network latency (~2ms)

---

### Strategy 3: Resilient (Fail-Open)

**Use Case**: High availability, graceful degradation

**Configuration**:
```yaml
# Profile: resilient
ratelimit:
  storage:
    type: TIERED       # Redis L1, Caffeine L2
  
  fail:
    strategy: OPEN     # Allow traffic if storage fails
  
  circuit-breaker:
    failure-threshold: 0.5
    timeout-seconds: 30
```

**Running**:
```bash
# Run with resilient profile
mvn spring-boot:run -Dspring.profiles.active=resilient

# Simulate Redis failure
docker stop ratelimit-redis

# Application stays alive, uses L2 cache
curl http://localhost:8080/api/public/data
# Still works!
```

**Characteristics**:
- ✅ Continues operating if Redis fails
- ✅ L1 (Redis) + L2 (Caffeine) tiered storage
- ✅ Circuit breaker protection
- ✅ Audit logging of failures
- ⚠️ Per-node limits during failover
- ⚠️ Total cluster traffic may exceed limit

---

## 📊 **Monitoring & Observability**

### Prometheus Metrics

**Available Metrics**:
```
# Request counters
ratelimit_requests_total{limiter="public-api",result="allowed"}
ratelimit_requests_total{limiter="public-api",result="denied"}

# Current usage
ratelimit_usage_current{limiter="user-profile-basic"}

# Latency
ratelimit_check_latency_seconds{limiter="partner-events"}

# Circuit breaker
ratelimit_circuit_state{limiter="public-api",state="open"}
```

**Query Examples**:
```promql
# Denial rate (requests/second)
rate(ratelimit_requests_total{result="denied"}[5m])

# Success rate
sum(rate(ratelimit_requests_total{result="allowed"}[5m]))
/ sum(rate(ratelimit_requests_total[5m]))

# 95th percentile latency
histogram_quantile(0.95, ratelimit_check_latency_seconds_bucket)

# Top denied limiters
topk(5, sum by (limiter) (ratelimit_requests_total{result="denied"}))
```

### Grafana Dashboard

**Access**: http://localhost:3000 (admin/admin)

**Pre-configured Panels**:
1. **Request Rate** - Allowed vs Denied requests/sec
2. **Denial Rate by Limiter** - Which endpoints are being rate limited
3. **Average Latency** - Performance of rate limit checks
4. **Bucket Usage %** - Current capacity utilization
5. **Circuit Breaker State** - System health
6. **Top Rate Limited IPs** - Identify abusers

---

## 🎓 **Learning Objectives**

### 1. How to Resolve Keys

**IP-based** (Public endpoints):
```java
@RateLimit(key = "#ip")
```

**User-based** (Authenticated users):
```java
@RateLimit(key = "#principal.username")
```

**Header-based** (Partners):
```java
@RateLimit(key = "#headers['X-Partner-ID']")
```

**Composite** (IP + User):
```java
@RateLimit(key = "#ip + ':' + #principal.username")
```

**Method args** (Resource-specific):
```java
@RateLimit(key = "'resource:' + #resourceId")
public void updateResource(@PathVariable String resourceId) { }
```

### 2. How to Choose Algorithms

**Fixed Window** (Simplest):
- ✅ Simple, predictable
- ✅ Low memory usage
- ❌ Allows bursts at window boundaries
- **Use for**: Public APIs, simple rate limiting

**Sliding Window** (Most accurate):
- ✅ No boundary issues
- ✅ Smooth rate limiting
- ❌ Slightly more memory
- **Use for**: User quotas, accurate limiting

**Token Bucket** (Burst-friendly):
- ✅ Allows controlled bursts
- ✅ Smooth sustained rate
- ❌ More complex configuration
- **Use for**: Partner APIs, batch processing

### 3. How to Monitor

**Metrics Integration**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  
  metrics:
    export:
      prometheus:
        enabled: true
```

**Audit Logging**:
```java
// Automatically logs to configured AuditLogger
// - Configuration changes
// - Enforcement actions (allow/deny)
// - System failures
```

**Custom Dashboards**:
- Import Grafana dashboard from `docker/grafana/dashboards/`
- Create alerts for high denial rates
- Monitor circuit breaker state

### 4. How to Handle Failures

**Fail-Open** (High availability):
```yaml
ratelimit:
  fail:
    strategy: OPEN     # Allow traffic if storage fails
```

**Fail-Closed** (High security):
```yaml
ratelimit:
  fail:
    strategy: CLOSED   # Deny traffic if storage fails
```

**Circuit Breaker**:
```yaml
ratelimit:
  circuit-breaker:
    failure-threshold: 0.5    # Trip at 50% failure rate
    timeout-seconds: 30       # 30s recovery window
```

---

## 🧪 **Testing Scenarios**

### Test 1: Verify IP-Based Limiting

```bash
#!/bin/bash
# test-ip-limiting.sh

echo "Testing IP-based rate limiting..."
for i in {1..15}; do
  echo "Request $i:"
  curl -s -w "\nHTTP Status: %{http_code}\n" \
    http://localhost:8080/api/public/data | jq .
  sleep 1
done
```

### Test 2: Verify Tier-Based Limiting

```bash
#!/bin/bash
# test-tier-limiting.sh

echo "Testing BASIC user (100 req/hour)..."
for i in {1..105}; do
  curl -s -u basic:password \
    http://localhost:8080/api/user/profile > /dev/null
  echo -n "."
done
echo ""
echo "Should see 429 after 100 requests"

echo "\nTesting PREMIUM user (1000 req/hour)..."
for i in {1..105}; do
  curl -s -u premium:password \
    http://localhost:8080/api/user/profile > /dev/null
  echo -n "."
done
echo ""
echo "Should NOT see 429 (only 105 requests)"
```

### Test 3: Verify Token Bucket Burst

```bash
#!/bin/bash
# test-token-bucket.sh

PARTNER_ID="partner-123"

echo "Testing token bucket burst..."
echo "Sending 100 requests rapidly (should all succeed)..."
for i in {1..100}; do
  curl -s -X POST \
    -H "X-Partner-ID: $PARTNER_ID" \
    -H "Content-Type: application/json" \
    -d "{\"event\":\"burst-$i\"}" \
    http://localhost:8080/api/partner/events > /dev/null
done
echo "100 requests sent successfully"

echo "\nSending 101st request (should fail)..."
HTTP_STATUS=$(curl -s -w "%{http_code}" -X POST \
  -H "X-Partner-ID: $PARTNER_ID" \
  -d '{"event":"overflow"}' \
  http://localhost:8080/api/partner/events)

if [ "$HTTP_STATUS" == "429" ]; then
  echo "✓ Correctly rate limited (429)"
else
  echo "✗ Expected 429, got $HTTP_STATUS"
fi

echo "\nWaiting 10 seconds for bucket refill..."
sleep 10

echo "Sending request after refill (should succeed)..."
curl -s -X POST \
  -H "X-Partner-ID: $PARTNER_ID" \
  -d '{"event":"after-wait"}' \
  http://localhost:8080/api/partner/events | jq .
```

---

## 📝 **Common Patterns**

### Pattern 1: Progressive Rate Limiting

```java
@RateLimits({
    @RateLimit(
        name = "search-per-second",
        key = "#ip",
        requests = 5,
        window = 1              // 5/second
    ),
    @RateLimit(
        name = "search-per-minute",
        key = "#ip",
        requests = 50,
        window = 60             // 50/minute
    ),
    @RateLimit(
        name = "search-per-hour",
        key = "#ip",
        requests = 1000,
        window = 3600           // 1000/hour
    )
})
@GetMapping("/search")
public List<Result> search() { }
```

### Pattern 2: Resource-Specific Limiting

```java
@RateLimit(
    name = "update-resource",
    key = "'resource:' + #id",
    requests = 10,
    window = 60
)
@PutMapping("/resources/{id}")
public Resource update(@PathVariable String id, @RequestBody Resource resource) { }
```

### Pattern 3: Combined IP + User Limiting

```java
@RateLimits({
    @RateLimit(
        name = "per-ip",
        key = "#ip",
        requests = 100,
        window = 60
    ),
    @RateLimit(
        name = "per-user",
        key = "#principal.username",
        requests = 50,
        window = 60
    )
})
```

---

## 🐛 **Troubleshooting**

### Issue: Redis Connection Failed

**Symptoms**: Application fails to start with Redis profile

**Solution**:
```bash
# Check Redis is running
docker ps | grep redis

# Check connectivity
redis-cli -h localhost -p 6379 ping

# Check logs
docker logs ratelimit-redis
```

### Issue: Metrics Not Appearing in Prometheus

**Symptoms**: No metrics in Prometheus dashboard

**Solution**:
```bash
# Verify actuator is exposing metrics
curl http://localhost:8080/actuator/prometheus

# Check Prometheus targets
# Go to http://localhost:9090/targets
# Verify "ratelimit-spring-boot" is UP

# Check Prometheus config
cat docker/prometheus/prometheus.yml
```

### Issue: Rate Limits Not Working

**Symptoms**: Can exceed configured limits

**Solution**:
```bash
# Check rate limit is enabled
curl http://localhost:8080/actuator/configprops | jq '.ratelimit'

# Check logs for rate limit decisions
# Should see: "Rate limit passed: limiter=public-api, remaining=9"

# Verify annotation is present
# Check controller source code
```

---

## 🎯 **Next Steps**

1. **Explore WebFlux Example** (Coming Soon)
   - Reactive rate limiting
   - Non-blocking storage
   - Functional routing

2. **Explore Standalone Example** (Coming Soon)
   - Direct engine usage
   - Custom key resolution
   - Programmatic configuration

3. **Customize Examples**
   - Modify rate limits
   - Add new endpoints
   - Integrate with your auth system

4. **Deploy to Production**
   - Use Redis cluster
   - Enable monitoring
   - Configure fail-open strategy

---

**Ready to start? Run the infrastructure and example:**

```bash
cd rl-examples/docker && docker-compose up -d
cd ../rl-example-spring-boot && mvn spring-boot:run
```

**Access the application:**
- http://localhost:8080/api/public/data
- http://localhost:9090 (Prometheus)
- http://localhost:3000 (Grafana)

Happy rate limiting! 🚀
