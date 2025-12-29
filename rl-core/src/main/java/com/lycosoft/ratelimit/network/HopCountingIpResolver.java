package com.lycosoft.ratelimit.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Advanced IP resolver with hop-counting mechanism to prevent IP spoofing.
 * 
 * <p>This resolver addresses security concerns in multi-layered cloud environments
 * (e.g., Cloudflare → Nginx → Application) where the X-Forwarded-For header can
 * be manipulated by clients.
 * 
 * <p><b>Problem:</b>
 * <pre>
 * Client sends: X-Forwarded-For: 127.0.0.1  (spoofed!)
 * Nginx adds:   X-Forwarded-For: 127.0.0.1, 1.2.3.4
 * App sees:     X-Forwarded-For: 127.0.0.1, 1.2.3.4
 * </pre>
 * 
 * If the app naively takes the first IP, rate limiting can be bypassed by spoofing
 * localhost or other trusted IPs.
 * 
 * <p><b>Solution: Hop Counting</b>
 * <pre>
 * Configuration: trusted-hops = 1 (we trust only Nginx)
 * Header:        X-Forwarded-For: spoofed_ip, client_ip, nginx_ip
 * Calculation:   Count 1 hop from the right → client_ip
 * Result:        client_ip is used for rate limiting
 * </pre>
 * 
 * <p><b>Security Requirements:</b>
 * <ul>
 *   <li>Only process hop counting if the immediate request source matches a trusted proxy CIDR</li>
 *   <li>If not from trusted proxy, use the direct connection IP</li>
 *   <li>Validate that trusted-hops doesn't exceed the XFF chain length</li>
 * </ul>
 * 
 * <p><b>Example Configuration:</b>
 * <pre>
 * ratelimit:
 *   proxy:
 *     trusted-hops: 2          # Trust last 2 proxies
 *     trusted-proxies:         # CIDR ranges of trusted proxies
 *       - 10.0.0.0/8           # Private network
 *       - 172.16.0.0/12        # Kubernetes network
 *       - 192.168.1.0/24       # Nginx pods
 * </pre>
 * 
 * @since 1.1.0
 */
public class HopCountingIpResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(HopCountingIpResolver.class);
    
    private final int trustedHops;
    private final Set<String> trustedProxyCidrs;
    private final List<CidrRange> trustedRanges;
    
    /**
     * Creates a hop-counting IP resolver.
     * 
     * @param trustedHops number of hops to trust from the right of XFF chain
     * @param trustedProxyCidrs set of trusted proxy CIDR ranges
     */
    public HopCountingIpResolver(int trustedHops, Set<String> trustedProxyCidrs) {
        if (trustedHops < 0) {
            throw new IllegalArgumentException("trustedHops must be >= 0");
        }
        
        this.trustedHops = trustedHops;
        this.trustedProxyCidrs = trustedProxyCidrs != null ? trustedProxyCidrs : Collections.emptySet();
        this.trustedRanges = parseCidrRanges(this.trustedProxyCidrs);
        
        logger.info("HopCountingIpResolver initialized: trustedHops={}, trustedProxies={}", 
                   trustedHops, this.trustedProxyCidrs);
    }
    
    /**
     * Resolves the client IP from X-Forwarded-For header using hop counting.
     * 
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Check if direct connection IP is from a trusted proxy</li>
     *   <li>If not trusted, return direct IP (client connected directly)</li>
     *   <li>If trusted, parse X-Forwarded-For header</li>
     *   <li>Count N hops from the right where N = trustedHops</li>
     *   <li>Return the IP at that position</li>
     * </ol>
     * 
     * @param xForwardedFor the X-Forwarded-For header value (comma-separated IPs)
     * @param directIp the direct connection IP (from socket)
     * @return the resolved client IP
     */
    public String resolveClientIp(String xForwardedFor, String directIp) {
        // Step 1: Validate direct IP is from trusted proxy
        if (!isTrustedProxy(directIp)) {
            logger.debug("Direct IP {} not in trusted proxies, using as client IP", directIp);
            return directIp;
        }
        
        // Step 2: If no XFF header, fall back to direct IP
        if (xForwardedFor == null || xForwardedFor.trim().isEmpty()) {
            logger.debug("No X-Forwarded-For header, using direct IP: {}", directIp);
            return directIp;
        }
        
        // Step 3: Parse XFF chain
        List<String> ipChain = parseXffHeader(xForwardedFor);
        if (ipChain.isEmpty()) {
            logger.warn("X-Forwarded-For header present but empty after parsing: {}", xForwardedFor);
            return directIp;
        }
        
        // Step 4: Hop counting logic
        if (trustedHops == 0) {
            // Trust no hops = use rightmost IP (most recent proxy added it)
            String resolvedIp = ipChain.get(ipChain.size() - 1);
            logger.debug("Trusted hops = 0, using rightmost IP: {}", resolvedIp);
            return resolvedIp;
        }
        
        // Count from right: trustedHops = 1 means skip 1 from right
        int targetIndex = ipChain.size() - trustedHops - 1;
        
        if (targetIndex < 0) {
            // Not enough IPs in chain for hop count
            logger.warn("Insufficient IPs in XFF chain. Chain length: {}, trustedHops: {}, using leftmost", 
                       ipChain.size(), trustedHops);
            return ipChain.get(0);
        }
        
        String resolvedIp = ipChain.get(targetIndex);
        logger.debug("Resolved client IP via hop counting: {} (hops={}, chain={})", 
                    resolvedIp, trustedHops, ipChain);
        
        return resolvedIp;
    }
    
    /**
     * Parses the X-Forwarded-For header into a list of IPs.
     * 
     * <p>The header format is: IP1, IP2, IP3 (comma-separated)
     * 
     * @param xForwardedFor the header value
     * @return list of IP addresses (left to right)
     */
    private List<String> parseXffHeader(String xForwardedFor) {
        return Arrays.stream(xForwardedFor.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .filter(this::isValidIp)
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if an IP is from a trusted proxy.
     * 
     * @param ip the IP address to check
     * @return true if the IP is in a trusted CIDR range
     */
    private boolean isTrustedProxy(String ip) {
        if (trustedRanges.isEmpty()) {
            // No trusted proxies configured = trust all
            return true;
        }
        
        try {
            InetAddress address = InetAddress.getByName(ip);
            for (CidrRange range : trustedRanges) {
                if (range.contains(address)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            logger.warn("Invalid IP address: {}", ip);
            return false;
        }
        
        return false;
    }
    
    /**
     * Basic IP validation.
     * 
     * @param ip the IP address string
     * @return true if valid
     */
    private boolean isValidIp(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            logger.debug("Invalid IP in XFF chain: {}", ip);
            return false;
        }
    }
    
    /**
     * Parses CIDR ranges from string set.
     * 
     * @param cidrs set of CIDR strings (e.g., "10.0.0.0/8")
     * @return list of parsed CIDR ranges
     */
    private List<CidrRange> parseCidrRanges(Set<String> cidrs) {
        List<CidrRange> ranges = new ArrayList<>();
        for (String cidr : cidrs) {
            try {
                ranges.add(new CidrRange(cidr));
            } catch (Exception e) {
                logger.error("Failed to parse CIDR range: {}", cidr, e);
            }
        }
        return ranges;
    }
    
    /**
     * Represents a CIDR range for IP matching.
     */
    private static class CidrRange {
        private final InetAddress network;
        private final int prefixLength;
        private final byte[] mask;
        
        CidrRange(String cidr) throws UnknownHostException {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
            }
            
            this.network = InetAddress.getByName(parts[0]);
            this.prefixLength = Integer.parseInt(parts[1]);
            this.mask = createMask(network.getAddress().length, prefixLength);
        }
        
        boolean contains(InetAddress address) {
            byte[] networkBytes = network.getAddress();
            byte[] addressBytes = address.getAddress();
            
            // Different IP version (IPv4 vs IPv6)
            if (networkBytes.length != addressBytes.length) {
                return false;
            }
            
            for (int i = 0; i < networkBytes.length; i++) {
                if ((networkBytes[i] & mask[i]) != (addressBytes[i] & mask[i])) {
                    return false;
                }
            }
            
            return true;
        }
        
        private byte[] createMask(int length, int prefixLength) {
            byte[] mask = new byte[length];
            int bitsRemaining = prefixLength;
            
            for (int i = 0; i < length; i++) {
                if (bitsRemaining >= 8) {
                    mask[i] = (byte) 0xFF;
                    bitsRemaining -= 8;
                } else if (bitsRemaining > 0) {
                    mask[i] = (byte) (0xFF << (8 - bitsRemaining));
                    bitsRemaining = 0;
                } else {
                    mask[i] = 0;
                }
            }
            
            return mask;
        }
    }
}
