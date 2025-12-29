# Advanced Networking & Adaptive Features

Complete guide to advanced rate limiting features including hop counting, adaptive throttling, and RFC 9457 compliance.

## 📋 Overview

This document covers the **Phase 5** advanced networking features:

1. **Hop Counting IP Resolution** - Prevents IP spoofing in multi-proxy environments
2. **Adaptive Throttling** - Graceful degradation before hard blocking
3. **RFC 9457 Problem Details** - Standardized machine-readable error responses

All features are **production-ready** and follow industry best practices from Cloudflare, AWS, and Netflix.

---

## 🔒 Hop Counting IP Resolution

### The Problem

In cloud deployments with CDN → Load Balancer → Application, clients can spoof the `X-Forwarded-For` header:

```
❌ ATTACK:
Client sends:  X-Forwarded-For: 127.0.0.1  (spoofed!)
Nginx adds:    X-Forwarded-For: 127.0.0.1, 1.2.3.4
App sees:      X-Forwarded-For: 127.0.0.1, 1.2.3.4

If app uses first IP → Rate limiting bypassed!
```

### The Solution

**Count backwards from the rightmost (most trusted) proxy**:

```yaml
Configuration: trusted-hops = 1 (we trust only our reverse proxy)

Header:        X-Forwarded-For: spoofed_ip, client_ip, nginx_ip
Calculation:   Count 1 hop from right → client_ip
Result:        ✅ client_ip is used for rate limiting
```

### Configuration Examples

**Single Reverse Proxy**:
```yaml
# Architecture: Internet → Nginx → App
ratelimit:
  proxy:
    trusted-hops: 1
    trusted-proxies:
      - 192.168.1.0/24  # Nginx subnet
```

**CDN + Load Balancer**:
```yaml
# Architecture: Internet → Cloudflare → AWS ALB → App
ratelimit:
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - 103.21.244.0/22    # Cloudflare IPs
      - 172.16.0.0/12      # AWS VPC
```

### Security Best Practices

⚠️ **CRITICAL**: Always configure `trusted-proxies` in production!

| Configuration | Security Level | Use Case |
|--------------|----------------|----------|
| Empty `trusted-proxies` | ❌ INSECURE | Never use in production |
| `trusted-proxies: ["0.0.0.0/0"]` | ❌ INSECURE | Trusts anyone |
| Explicit CIDRs | ✅ SECURE | Production-ready |

---

## ⚡ Adaptive Throttling

### Concept

Instead of hard-blocking at 100%, gradually slow down traffic:

```
Soft Limit (80%):  No throttling, normal speed
90%:               1000ms delay (requests slowed)
95%:               1750ms delay (heavily throttled)
Hard Limit (100%): 2000ms delay → THEN block with 429
```

### Benefits

1. **Better UX**: Slowdown instead of errors
2. **Anti-Scraping**: Exponential delays discourage bots
3. **Load Smoothing**: Reduces traffic spikes
4. **Early Warning**: Signals before complete blocking

### Configuration

```yaml
ratelimit:
  throttle:
    enabled: true
    soft-limit-percentage: 80    # Start throttling at 80%
    max-delay-ms: 2000           # Max delay at 100%
    strategy: LINEAR             # LINEAR or EXPONENTIAL
```

### Strategies Compared

**LINEAR (Predictable)**:
```
Usage → Delay
80%   → 0ms
85%   → 500ms   (25% of max)
90%   → 1000ms  (50% of max)
100%  → 2000ms  (100% of max)
```

**EXPONENTIAL (Aggressive)**:
```
Usage → Delay
80%   → 0ms
85%   → 125ms   (minimal)
90%   → 500ms   (moderate)
100%  → 2000ms  (aggressive)
```

### Real-World Examples

**Public API (Discourage Scrapers)**:
```yaml
throttle:
  soft-limit-percentage: 70    # Early throttling
  max-delay-ms: 5000           # Aggressive
  strategy: EXPONENTIAL        # Rapid escalation
```

**User-Facing API (Good UX)**:
```yaml
throttle:
  soft-limit-percentage: 90    # Late throttling
  max-delay-ms: 1000           # Minimal
  strategy: LINEAR             # Predictable
```

---

## 📄 RFC 9457 Problem Details

### Standardized Error Responses

Machine-readable HTTP errors that clients can parse automatically.

### Example Response

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 24
RateLimit-Policy: 100;w=60

{
  "type": "https://ratelimit.io/probs/too-many-requests",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Quota exceeded. Please try again in 24 seconds.",
  "instance": "/api/v1/orders",
  "retry_after": 24,
  "limit": 100,
  "remaining": 0,
  "reset": 1640995200
}
```

### Client Integration

**JavaScript**:
```javascript
fetch('/api/orders', { method: 'POST' })
  .catch(async (err) => {
    if (err.status === 429) {
      const problem = await err.json();
      console.log(`Retry after ${problem.retry_after}s`);
      setTimeout(() => retry(), problem.retry_after * 1000);
    }
  });
```

**Java**:
```java
try {
    rest.post("/api/orders", order);
} catch (TooManyRequests ex) {
    ProblemDetail problem = parseProblem(ex.getBody());
    Thread.sleep(problem.getRetryAfter() * 1000);
    rest.post("/api/orders", order);  // Retry
}
```

---

## ⚙️ Complete Configuration

```yaml
ratelimit:
  enabled: true
  
  # Hop Counting
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - 103.21.244.0/22    # Cloudflare
      - 10.0.0.0/8         # Private network
  
  # Adaptive Throttling
  throttle:
    enabled: true
    soft-limit-percentage: 80
    max-delay-ms: 2000
    strategy: LINEAR
  
  # RFC 9457
  problem-details:
    enabled: true
    include-extensions: true
```

---

## 🔐 Security Considerations

### Hop Counting

1. **Always Configure Trusted Proxies**
   ```yaml
   # ❌ DON'T
   trusted-proxies: []
   
   # ✅ DO
   trusted-proxies: ["10.0.0.0/8", "172.16.0.0/12"]
   ```

2. **Verify Architecture**
   ```bash
   # Test hop counting
   curl -H "X-Forwarded-For: spoofed, real" https://api.example.com
   # Check logs: Should resolve to "real", not "spoofed"
   ```

### Adaptive Throttling

1. **Thread Pool Exhaustion Risk**
   - Long delays can exhaust threads
   - Monitor thread pool utilization
   - Cap `max-delay-ms` to reasonable value (2-5s)

2. **Resource Management**
   - Release database connections before delay
   - Set transaction timeout < `max-delay-ms`

### Problem Details

1. **Information Disclosure**
   ```yaml
   # Production: Hide internal details
   problem-details:
     include-extensions: false
   ```

---

## 🚀 Quick Start

**1. Add Dependency**:
```xml
<dependency>
    <groupId>com.lycosoft</groupId>
    <artifactId>rl-adapter-spring</artifactId>
    <version>1.1.0</version>
</dependency>
```

**2. Configure**:
```yaml
ratelimit:
  proxy:
    trusted-hops: 1
    trusted-proxies: ["10.0.0.0/8"]
  throttle:
    enabled: true
```

**3. Use**:
```java
@RateLimit(key = "#ip", requests = 100, window = 60)
@GetMapping("/api/data")
public Data getData() {
    return service.fetchData();
}
```

**4. Test**:
```bash
for i in {1..110}; do
  curl -w "Time: %{time_total}s\n" https://api.example.com/data
done
```

---

## 📚 References

- [RFC 9457: Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html)
- [RFC 7231: Retry-After](https://www.rfc-editor.org/rfc/rfc7231#section-7.1.3)
- [Cloudflare: Trusted Proxies](https://developers.cloudflare.com/fundamentals/get-started/reference/cloudflare-ip-addresses/)

---

**Phase 5 Advanced Features - Production Ready!** 🚀
