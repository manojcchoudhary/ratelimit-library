package com.lycosoft.ratelimit.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves client IP addresses from X-Forwarded-For headers using hop-counting.
 * 
 * <p>This implementation prevents IP spoofing in multi-layered cloud environments
 * (e.g., Cloudflare → Nginx → Application) by counting trusted hops from the right.
 * 
 * <p><b>Problem:</b>
 * In multi-proxy environments, blindly trusting the first IP in X-Forwarded-For
 * allows clients to spoof their IP address by setting a custom XFF header.
 * 
 * <p><b>Solution:</b>
 * Count backwards from the right (most recent proxy) by the number of trusted hops.
 * Only process XFF if the immediate request source matches a trusted proxy CIDR.
 * 
 * <p><b>Example:</b>
 * <pre>
 * X-Forwarded-For: malicious_ip, real_client_ip, cloudflare_ip, nginx_ip
 * Remote Address: nginx_ip (matches trusted proxy)
 * Trusted Hops: 2
 * 
 * Resolution:
 * - Start from right: [nginx_ip, cloudflare_ip, real_client_ip, malicious_ip]
 * - Count 2 hops back: real_client_ip
 * - Result: real_client_ip (malicious_ip ignored)
 * </pre>
 * 
 * @since 1.0.0
 */
public class TrustedProxyResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(TrustedProxyResolver.class);
    
    /**
     * Number of trusted hops to count from the right.
     * Default: 1 (one trusted proxy)
     */
    private final int trustedHops;
    
    /**
     * Set of trusted proxy CIDR ranges.
     * Only process XFF if remote address matches one of these.
     */
    private final Set<CidrRange> trustedProxies;
    
    /**
     * Creates a trusted proxy resolver with default settings.
     * 
     * <p>Defaults:
     * <ul>
     *   <li>Trusted hops: 1</li>
     *   <li>Trusted proxies: 127.0.0.0/8, ::1/128 (localhost only)</li>
     * </ul>
     */
    public TrustedProxyResolver() {
        this(1, Set.of("127.0.0.0/8", "::1/128"));
    }
    
    /**
     * Creates a trusted proxy resolver with custom settings.
     * 
     * @param trustedHops number of hops to count from right
     * @param trustedProxyCidrs set of trusted proxy CIDR ranges
     */
    public TrustedProxyResolver(int trustedHops, Set<String> trustedProxyCidrs) {
        if (trustedHops < 0) {
            throw new IllegalArgumentException("trustedHops must be >= 0");
        }
        
        this.trustedHops = trustedHops;
        this.trustedProxies = new HashSet<>();
        
        for (String cidr : trustedProxyCidrs) {
            try {
                trustedProxies.add(CidrRange.parse(cidr));
            } catch (Exception e) {
                logger.warn("Invalid CIDR range '{}': {}", cidr, e.getMessage());
            }
        }
        
        logger.info("TrustedProxyResolver initialized: hops={}, proxies={}", 
                   trustedHops, trustedProxies.size());
    }
    
    /**
     * Resolves the client IP from X-Forwarded-For header.
     * 
     * @param xForwardedFor the X-Forwarded-For header value (comma-separated IPs)
     * @param remoteAddress the immediate request source IP
     * @return the resolved client IP, or remoteAddress if XFF cannot be trusted
     */
    public String resolveClientIp(String xForwardedFor, String remoteAddress) {
        // If no XFF header, use remote address
        if (xForwardedFor == null || xForwardedFor.trim().isEmpty()) {
            logger.trace("No X-Forwarded-For header, using remote address: {}", remoteAddress);
            return remoteAddress;
        }
        
        // Verify remote address is a trusted proxy
        if (!isTrustedProxy(remoteAddress)) {
            logger.debug("Remote address {} is not a trusted proxy, ignoring XFF", remoteAddress);
            return remoteAddress;
        }
        
        // Parse X-Forwarded-For header
        List<String> ips = parseXForwardedFor(xForwardedFor);
        
        if (ips.isEmpty()) {
            logger.warn("X-Forwarded-For header is empty after parsing");
            return remoteAddress;
        }
        
        // Count hops from the right
        int index = ips.size() - trustedHops;
        
        if (index < 0) {
            logger.warn("Not enough IPs in XFF for {} hops (found {}), using first IP", 
                       trustedHops, ips.size());
            return ips.get(0);
        }
        
        String clientIp = ips.get(index);
        logger.trace("Resolved client IP: {} (from XFF with {} hops)", clientIp, trustedHops);
        
        return clientIp;
    }
    
    /**
     * Checks if an IP address is a trusted proxy.
     * 
     * @param ipAddress the IP address to check
     * @return true if the IP is in a trusted proxy CIDR range
     */
    private boolean isTrustedProxy(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            
            for (CidrRange cidr : trustedProxies) {
                if (cidr.contains(addr)) {
                    return true;
                }
            }
            
            return false;
            
        } catch (UnknownHostException e) {
            logger.warn("Invalid IP address '{}': {}", ipAddress, e.getMessage());
            return false;
        }
    }
    
    /**
     * Parses X-Forwarded-For header into list of valid IP addresses.
     *
     * <p><b>Security:</b> Each IP is validated using {@link InetAddress#getByName(String)}
     * to prevent malformed IPs from being returned as client IPs.
     *
     * @param xForwardedFor the header value
     * @return list of valid IP addresses (trimmed, whitespace removed, validated)
     */
    private List<String> parseXForwardedFor(String xForwardedFor) {
        List<String> ips = new ArrayList<>();

        String[] parts = xForwardedFor.split(",");
        for (String part : parts) {
            String ip = part.trim();
            if (!ip.isEmpty()) {
                // SECURITY: Validate each IP before adding to prevent malformed IPs
                if (isValidIpAddress(ip)) {
                    ips.add(ip);
                } else {
                    logger.warn("Invalid IP address in X-Forwarded-For header: '{}', skipping",
                            ip.length() > 50 ? ip.substring(0, 50) + "..." : ip);
                }
            }
        }

        return ips;
    }

    /**
     * Validates an IP address string.
     *
     * @param ip the IP address to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) { // Max IPv6 length
            return false;
        }

        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Gets the configured number of trusted hops.
     * 
     * @return the trusted hops count
     */
    public int getTrustedHops() {
        return trustedHops;
    }
    
    /**
     * Represents a CIDR range (e.g., 192.168.0.0/16).
     */
    private static class CidrRange {
        private final InetAddress networkAddress;
        private final int prefixLength;
        private final byte[] mask;
        
        private CidrRange(InetAddress networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
            this.mask = createMask(networkAddress.getAddress().length, prefixLength);
        }
        
        /**
         * Parses a CIDR string (e.g., "192.168.0.0/16").
         * 
         * @param cidr the CIDR string
         * @return the CIDR range
         * @throws IllegalArgumentException if CIDR is invalid
         */
        public static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/");
            
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
            }
            
            try {
                InetAddress addr = InetAddress.getByName(parts[0]);
                int prefix = Integer.parseInt(parts[1]);
                
                int maxPrefix = addr.getAddress().length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException(
                        "Invalid prefix length: " + prefix + " (max: " + maxPrefix + ")"
                    );
                }
                
                return new CidrRange(addr, prefix);
                
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address in CIDR: " + parts[0], e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid prefix length: " + parts[1], e);
            }
        }
        
        /**
         * Checks if an IP address is within this CIDR range.
         * 
         * @param address the IP address to check
         * @return true if the address is in range
         */
        public boolean contains(InetAddress address) {
            byte[] addrBytes = address.getAddress();
            byte[] netBytes = networkAddress.getAddress();
            
            // Different address families (IPv4 vs IPv6)
            if (addrBytes.length != netBytes.length) {
                return false;
            }
            
            // Apply mask and compare
            for (int i = 0; i < addrBytes.length; i++) {
                if ((addrBytes[i] & mask[i]) != (netBytes[i] & mask[i])) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * Creates a subnet mask for the given prefix length.
         * 
         * @param byteCount number of bytes (4 for IPv4, 16 for IPv6)
         * @param prefixLength the prefix length
         * @return the mask as byte array
         */
        private static byte[] createMask(int byteCount, int prefixLength) {
            byte[] mask = new byte[byteCount];
            
            int remainingBits = prefixLength;
            for (int i = 0; i < byteCount; i++) {
                if (remainingBits >= 8) {
                    mask[i] = (byte) 0xFF;
                    remainingBits -= 8;
                } else if (remainingBits > 0) {
                    mask[i] = (byte) (0xFF << (8 - remainingBits));
                    remainingBits = 0;
                } else {
                    mask[i] = 0;
                }
            }
            
            return mask;
        }
        
        @Override
        public String toString() {
            return networkAddress.getHostAddress() + "/" + prefixLength;
        }
    }
}
