# IP Address Resolution Utilities

## Overview

The rate limiting library provides two complementary approaches for resolving client IP addresses in proxied environments:

1. **IpAddressUtils** - Comprehensive header detection (simple, works out-of-the-box)
2. **TrustedProxyResolver** - Secure hop-counting with CIDR validation (production-grade security)

---

## IpAddressUtils (Simple Approach)

### When to Use

- ✅ Development and testing environments
- ✅ Single proxy setups (e.g., just Nginx)
- ✅ When you trust all proxy headers
- ✅ Quick setup without configuration
- ✅ Debugging IP resolution issues

### How It Works

Checks headers in priority order and returns the first valid public IP:

```java
Priority Order:
1. CF-Connecting-IP (Cloudflare - highest trust)
2. True-Client-IP (Cloudflare Enterprise)
3. X-Forwarded-For (Standard proxy)
4. X-Real-IP (Nginx)
5. ... 20+ more headers
6. RemoteAddr (fallback)
```

### Usage Examples

**Basic IP Resolution**:
```java
import com.lycosoft.ratelimit.util.IpAddressUtils;

String clientIp = IpAddressUtils.getClientIpAddress(request);
// Returns: "203.0.113.42"
```

**Detailed IP Information**:
```java
IpAddressUtils.IpInfo info = IpAddressUtils.getClientIpInfo(request);

System.out.println(info.getIpAddress());  // "203.0.113.42"
System.out.println(info.getSource());     // "CF-Connecting-IP"
System.out.println(info.isValid());       // true
System.out.println(info.isPublicIp());    // true
System.out.println(info.isPrivateIp());   // false
```

**Debugging All IPs**:
```java
List<String> allIps = IpAddressUtils.getAllIpAddresses(request);
// ["203.0.113.42", "198.51.100.1", "192.0.2.1"]
```

**With Trusted Proxies**:
```java
List<String> trustedProxies = Arrays.asList("10.0.1.5", "10.0.1.6");

if (IpAddressUtils.isFromTrustedProxy(request, trustedProxies)) {
    String clientIp = IpAddressUtils.getClientIpWithTrustedProxies(request, trustedProxies);
}
```

### Security Considerations

⚠️ **WARNING**: IpAddressUtils is vulnerable to IP spoofing!

**Attack Example**:
```http
GET /api/resource HTTP/1.1
X-Forwarded-For: 1.2.3.4, 5.6.7.8
CF-Connecting-IP: 9.10.11.12

IpAddressUtils returns: 9.10.11.12
Problem: Attacker can set CF-Connecting-IP header!
```

**When It's Safe**:
- Behind Cloudflare (strips client headers)
- Behind AWS ALB (trusted headers)
- Development/testing only

**When It's NOT Safe**:
- Direct internet exposure
- Untrusted proxies
- Production rate limiting
- Security-critical decisions

---

## TrustedProxyResolver (Secure Approach)

### When to Use

- ✅ Production environments
- ✅ Multi-proxy setups (CDN → LB → App)
- ✅ Security-critical rate limiting
- ✅ Preventing IP spoofing
- ✅ Compliance requirements

### How It Works

Uses **hop-counting with CIDR validation**:

```
X-Forwarded-For: malicious_ip, real_client_ip, cloudflare_ip
Remote Address: nginx_ip

Configuration:
- trusted-hops: 2
- trusted-proxies: 10.0.0.0/8

Process:
1. Verify nginx_ip ∈ 10.0.0.0/8 ✓
2. Parse XFF: [malicious_ip, real_client_ip, cloudflare_ip]
3. Count 2 hops from right: real_client_ip
4. Result: real_client_ip (malicious_ip ignored!)
```

### Configuration

**Spring Boot**:
```yaml
ratelimit:
  proxy:
    trusted-hops: 2
    trusted-proxies:
      - "104.16.0.0/12"    # Cloudflare
      - "10.0.0.0/8"       # Internal network
```

**Quarkus**:
```properties
ratelimit.proxy.trusted-hops=2
ratelimit.proxy.trusted-proxies=104.16.0.0/12,10.0.0.0/8
```

### Architecture Examples

**Example 1: Cloudflare + Nginx**
```
Client → Cloudflare → Nginx → App

X-Forwarded-For: client_ip, cloudflare_ip
Remote: nginx_ip (10.0.1.5)

Config:
trusted-hops: 2
trusted-proxies: 10.0.0.0/8

Result: client_ip ✓
```

**Example 2: AWS ALB + K8s**
```
Client → Cloudflare → AWS ALB → K8s Ingress → App

X-Forwarded-For: client_ip, cf_ip, alb_ip
Remote: k8s_ingress (10.0.2.10)

Config:
trusted-hops: 3
trusted-proxies: 10.0.0.0/8

Result: client_ip ✓
```

**Example 3: Misconfigured (VULNERABLE)**
```
Client → Cloudflare → Nginx → App

X-Forwarded-For: malicious_ip, real_ip, cf_ip
Remote: nginx_ip (10.0.1.5)

WRONG Config:
trusted-hops: 1  ← INCORRECT!
trusted-proxies: 10.0.0.0/8

Result: cf_ip ✗ (should be real_ip)

CORRECT Config:
trusted-hops: 2  ← Count Cloudflare + Nginx
trusted-proxies: 10.0.0.0/8

Result: real_ip ✓
```

---

## Comparison Matrix

| Feature | IpAddressUtils | TrustedProxyResolver |
|---------|----------------|---------------------|
| **Setup** | Zero config | Requires CIDR + hop count |
| **Security** | ⚠️ Low (spoofable) | ✅ High (CIDR validated) |
| **Headers** | 20+ headers | X-Forwarded-For only |
| **CDN Support** | ✅ Auto-detects | ⚠️ Needs hop config |
| **Multi-Proxy** | ❌ First valid IP | ✅ Hop counting |
| **IP Spoofing** | ❌ Vulnerable | ✅ Protected |
| **Use Case** | Dev/Testing | Production |
| **Performance** | ~5μs | ~10μs |

---

## Migration Path

### Phase 1: Development (IpAddressUtils)
```java
// Quick and easy
String ip = IpAddressUtils.getClientIpAddress(request);
```

### Phase 2: Staging (TrustedProxyResolver)
```yaml
# Test hop-counting configuration
ratelimit:
  proxy:
    trusted-hops: 1  # Start with 1, increase if wrong
    trusted-proxies:
      - "10.0.0.0/8"
```

### Phase 3: Production (TrustedProxyResolver + Monitoring)
```yaml
ratelimit:
  proxy:
    trusted-hops: 2  # Verified in staging
    trusted-proxies:
      - "104.16.0.0/12"  # Cloudflare
      - "10.0.0.0/8"     # Internal
```

---

## Supported Headers (IpAddressUtils)

### CDN Headers
- **Cloudflare**: `CF-Connecting-IP`, `True-Client-IP`
- **Fastly**: `Fastly-Client-IP`
- **Akamai**: `Akamai-Origin-Hop`
- **Azure**: `X-Azure-ClientIP`, `X-Azure-SocketIP`

### Standard Proxy Headers
- **Nginx**: `X-Real-IP`
- **Apache**: `Proxy-Client-IP`
- **WebLogic**: `WL-Proxy-Client-IP`
- **Generic**: `X-Forwarded-For`, `X-Client-IP`

### RFC 7239 (Forwarded Header)
- `Forwarded`
- `Forwarded-For`
- `X-Forwarded`

### Alternative Headers
- `HTTP_X_FORWARDED_FOR`
- `HTTP_CLIENT_IP`
- `HTTP_FORWARDED`
- `X-Cluster-Client-IP`

---

## Validation Features

### IPv4 Validation
```java
IpAddressUtils.isValidIpAddress("203.0.113.42");  // true
IpAddressUtils.isValidIpAddress("999.0.0.1");     // false
```

### IPv6 Validation
```java
IpAddressUtils.isValidIpAddress("2001:0db8::1");  // true
IpAddressUtils.isValidIpAddress("invalid:::");    // false
```

### Private IP Detection
```java
IpAddressUtils.isPrivateIpAddress("192.168.1.1");   // true
IpAddressUtils.isPrivateIpAddress("10.0.0.5");      // true
IpAddressUtils.isPrivateIpAddress("203.0.113.42");  // false
```

**Detected Private Ranges**:
- `10.0.0.0/8` (Class A)
- `172.16.0.0/12` (Class B)
- `192.168.0.0/16` (Class C)
- `127.0.0.0/8` (Loopback)
- `169.254.0.0/16` (Link-local)
- `fc00::/7` (IPv6 private)
- `fe80::/10` (IPv6 link-local)

---

## Best Practices

### ✅ DO

1. **Use TrustedProxyResolver in production**
   ```yaml
   ratelimit.proxy.trusted-hops: 2
   ratelimit.proxy.trusted-proxies: 104.16.0.0/12,10.0.0.0/8
   ```

2. **Test hop configuration in staging**
   ```bash
   # Send test request with fake XFF
   curl -H "X-Forwarded-For: fake, real, proxy" https://staging.api.com
   
   # Check logs for resolved IP
   # Should be "real", not "fake" or "proxy"
   ```

3. **Monitor IP resolution**
   ```java
   IpInfo info = IpAddressUtils.getClientIpInfo(request);
   logger.info("Resolved IP: {} from {}", info.getIpAddress(), info.getSource());
   ```

### ❌ DON'T

1. **Don't use IpAddressUtils for security-critical rate limiting**
   ```java
   // VULNERABLE in production!
   String ip = IpAddressUtils.getClientIpAddress(request);
   ```

2. **Don't blindly trust all proxy headers**
   ```java
   // Attacker can spoof these!
   String ip = request.getHeader("CF-Connecting-IP");
   ```

3. **Don't misconfigure hop count**
   ```yaml
   # WRONG: You have 2 proxies but configured 1
   ratelimit.proxy.trusted-hops: 1  # ← Will resolve wrong IP!
   ```

---

## Troubleshooting

### Issue: Wrong IP Resolved

**Symptoms**: Rate limiting affects wrong users

**Solution**: Verify hop count
```bash
# Check X-Forwarded-For in logs
X-Forwarded-For: 1.2.3.4, 5.6.7.8, 9.10.11.12

# Count IPs from right:
# - Position 0 (right): 9.10.11.12 (last proxy)
# - Position 1: 5.6.7.8 (middle proxy)
# - Position 2: 1.2.3.4 (client) ← This is what you want

# Set trusted-hops: 2
```

### Issue: IP Spoofing Detected

**Symptoms**: Attackers bypassing rate limits

**Solution**: Switch to TrustedProxyResolver
```yaml
# Before (vulnerable)
# Using IpAddressUtils

# After (secure)
ratelimit:
  proxy:
    trusted-hops: 2
    trusted-proxies: [10.0.0.0/8]
```

### Issue: All IPs Show as Private

**Symptoms**: All requests rate-limited together

**Solution**: Check proxy configuration
```yaml
# Verify proxies are in trusted-proxies
ratelimit.proxy.trusted-proxies: 10.0.0.0/8,172.16.0.0/12
```

---

## Summary

- **Development**: Use `IpAddressUtils` for convenience
- **Production**: Use `TrustedProxyResolver` for security
- **Hybrid**: Use both (TrustedProxyResolver with IpAddressUtils fallback in Spring)
- **Always**: Test hop configuration before production deployment

---

**Choose wisely based on your security requirements!** 🔒
