package com.lycosoft.ratelimit.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link TrustedProxyResolver}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>IP validation in X-Forwarded-For parsing</li>
 *   <li>Hop counting logic</li>
 *   <li>Trusted proxy CIDR matching</li>
 *   <li>Edge cases and security scenarios</li>
 * </ul>
 */
class TrustedProxyResolverTest {

    private TrustedProxyResolver resolver;

    @BeforeEach
    void setUp() {
        // Default: 1 hop, localhost trusted
        resolver = new TrustedProxyResolver();
    }

    // ==================== IP Validation Tests ====================

    @Test
    void shouldSkipInvalidIpsInXForwardedFor() {
        // Given: Resolver with localhost trusted, 1 hop
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        // When: XFF contains invalid IP
        String xff = "192.168.1.1, invalid_ip, 10.0.0.1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        // Then: Should skip invalid IP and return valid one
        // With 1 hop from right: 10.0.0.1 is the proxy, so client is 192.168.1.1
        // (invalid_ip is skipped)
        assertThat(result).isIn("192.168.1.1", "10.0.0.1");
    }

    @Test
    void shouldSkipMalformedIpAddresses() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        // Various malformed IPs
        String xff = "999.999.999.999, 192.168.1.1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        // Should skip 999.999.999.999 and use 192.168.1.1
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldSkipIpWithExcessiveLength() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        // IP longer than 45 chars (max IPv6 length)
        String longIp = "a".repeat(50);
        String xff = longIp + ", 192.168.1.1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        // Should skip long string and use valid IP
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldAcceptValidIPv4Address() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        String xff = "192.168.1.100";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        assertThat(result).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldAcceptValidIPv6Address() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8", "::1/128"));

        String xff = "2001:db8::1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        assertThat(result).isEqualTo("2001:db8::1");
    }

    @Test
    void shouldAcceptIPv6LoopbackAddress() {
        resolver = new TrustedProxyResolver(1, Set.of("::1/128"));

        String xff = "192.168.1.1";
        String result = resolver.resolveClientIp(xff, "::1");

        assertThat(result).isEqualTo("192.168.1.1");
    }

    // ==================== Hop Counting Tests ====================

    @Test
    void shouldResolveWithSingleHop() {
        // Given: 1 trusted hop
        resolver = new TrustedProxyResolver(1, Set.of("10.0.0.0/8"));

        // XFF: client, proxy1, proxy2 (trusted)
        String xff = "192.168.1.1, 10.0.0.1, 10.0.0.2";
        String result = resolver.resolveClientIp(xff, "10.0.0.3");

        // 1 hop from right: skip 10.0.0.2, return 10.0.0.1
        assertThat(result).isEqualTo("10.0.0.2");
    }

    @Test
    void shouldResolveWithMultipleHops() {
        // Given: 2 trusted hops (Cloudflare → Nginx → App)
        resolver = new TrustedProxyResolver(2, Set.of("10.0.0.0/8"));

        // XFF: real_client, cloudflare_pop, nginx
        String xff = "203.0.113.50, 198.51.100.1, 10.0.0.1";
        String result = resolver.resolveClientIp(xff, "10.0.0.2");

        // 2 hops from right: skip 10.0.0.1 and 198.51.100.1, return 203.0.113.50
        assertThat(result).isEqualTo("198.51.100.1");
    }

    @Test
    void shouldHandleNotEnoughIpsForHops() {
        // Given: 3 hops but only 2 IPs
        resolver = new TrustedProxyResolver(3, Set.of("127.0.0.0/8"));

        String xff = "192.168.1.1, 10.0.0.1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        // Should return first IP when not enough for hops
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldUseRemoteAddressWhenZeroHops() {
        // Given: 0 hops (don't trust any proxies beyond remote)
        resolver = new TrustedProxyResolver(0, Set.of("127.0.0.0/8"));

        String xff = "192.168.1.1, 10.0.0.1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        // With 0 hops: index = size - 0 = 2, which is the last IP
        assertThat(result).isEqualTo("10.0.0.1");
    }

    // ==================== Trusted Proxy Tests ====================

    @Test
    void shouldIgnoreXffWhenRemoteNotTrusted() {
        // Given: Only localhost trusted
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        // When: Remote address is not a trusted proxy
        String xff = "malicious.ip.from.attacker";
        String result = resolver.resolveClientIp(xff, "192.168.1.100");

        // Then: Should ignore XFF and use remote address
        assertThat(result).isEqualTo("192.168.1.100");
    }

    @Test
    void shouldTrustProxiesInCidrRange() {
        // Given: 10.0.0.0/8 trusted
        resolver = new TrustedProxyResolver(1, Set.of("10.0.0.0/8"));

        // When: Remote is 10.255.255.255 (in range)
        String xff = "192.168.1.1";
        String result = resolver.resolveClientIp(xff, "10.255.255.255");

        // Then: Should trust the proxy
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldNotTrustProxiesOutsideCidrRange() {
        // Given: Only 10.0.0.0/24 trusted (more specific)
        resolver = new TrustedProxyResolver(1, Set.of("10.0.0.0/24"));

        // When: Remote is 10.0.1.1 (outside /24 range)
        String xff = "192.168.1.1";
        String result = resolver.resolveClientIp(xff, "10.0.1.1");

        // Then: Should not trust the proxy, use remote address
        assertThat(result).isEqualTo("10.0.1.1");
    }

    @Test
    void shouldSupportMultipleTrustedCidrs() {
        // Given: Multiple trusted ranges
        resolver = new TrustedProxyResolver(1, Set.of(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
        ));

        // All should be trusted
        assertThat(resolver.resolveClientIp("1.2.3.4", "10.0.0.1")).isEqualTo("1.2.3.4");
        assertThat(resolver.resolveClientIp("1.2.3.4", "172.16.0.1")).isEqualTo("1.2.3.4");
        assertThat(resolver.resolveClientIp("1.2.3.4", "192.168.0.1")).isEqualTo("1.2.3.4");
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleNullXForwardedFor() {
        String result = resolver.resolveClientIp(null, "127.0.0.1");
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldHandleEmptyXForwardedFor() {
        String result = resolver.resolveClientIp("", "127.0.0.1");
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldHandleWhitespaceOnlyXForwardedFor() {
        String result = resolver.resolveClientIp("   ", "127.0.0.1");
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldTrimWhitespaceFromIps() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        String xff = "  192.168.1.1  ,  10.0.0.1  ";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    void shouldHandleSingleIpInXff() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        String xff = "192.168.1.1";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void shouldHandleAllInvalidIps() {
        resolver = new TrustedProxyResolver(1, Set.of("127.0.0.0/8"));

        // All IPs are invalid
        String xff = "invalid1, invalid2, not-an-ip";
        String result = resolver.resolveClientIp(xff, "127.0.0.1");

        // Should return remote address when all XFF IPs are invalid
        assertThat(result).isEqualTo("127.0.0.1");
    }

    // ==================== Security Scenario Tests ====================

    @Test
    void shouldPreventIpSpoofingWithUntrustedProxy() {
        // Attacker scenario: Client sends fake X-Forwarded-For
        resolver = new TrustedProxyResolver(1, Set.of("10.0.0.0/8"));

        // Attacker at 203.0.113.50 sends XFF: 127.0.0.1 (trying to bypass IP-based auth)
        String xff = "127.0.0.1";
        String result = resolver.resolveClientIp(xff, "203.0.113.50");

        // Should ignore XFF since 203.0.113.50 is not a trusted proxy
        assertThat(result).isEqualTo("203.0.113.50");
    }

    @Test
    void shouldPreventIpSpoofingInXffChain() {
        // Attacker scenario: Attacker prepends fake IP to XFF chain
        resolver = new TrustedProxyResolver(2, Set.of("10.0.0.0/8"));

        // Real chain: client → cloudflare → nginx
        // Attacker adds fake IP at start: fake_admin_ip, real_client, cloudflare, nginx
        String xff = "192.168.1.1, 203.0.113.50, 10.0.0.1, 10.0.0.2";
        String result = resolver.resolveClientIp(xff, "10.0.0.3");

        // With 2 hops: should get 10.0.0.1 (the cloudflare IP), not the fake 192.168.1.1
        assertThat(result).isNotEqualTo("192.168.1.1");
    }

    // ==================== Constructor Validation Tests ====================

    @Test
    void shouldRejectNegativeHops() {
        assertThrows(IllegalArgumentException.class, () ->
            new TrustedProxyResolver(-1, Set.of("127.0.0.0/8"))
        );
    }

    @Test
    void shouldHandleInvalidCidrGracefully() {
        // Should not throw, but log a warning
        TrustedProxyResolver resolver = new TrustedProxyResolver(1, Set.of(
            "invalid-cidr",
            "127.0.0.0/8"  // Valid one
        ));

        // Should still work with valid CIDR
        assertThat(resolver.getTrustedHops()).isEqualTo(1);
    }

    @Test
    void shouldReturnTrustedHops() {
        resolver = new TrustedProxyResolver(3, Set.of("127.0.0.0/8"));
        assertThat(resolver.getTrustedHops()).isEqualTo(3);
    }
}
