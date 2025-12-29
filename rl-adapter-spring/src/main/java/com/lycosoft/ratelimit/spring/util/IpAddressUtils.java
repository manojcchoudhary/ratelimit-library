package com.lycosoft.ratelimit.spring.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for extracting client IP addresses from HTTP requests.
 * 
 * <p>Handles various proxy headers and CDN configurations including:
 * <ul>
 *   <li>X-Forwarded-For (most common proxy header)</li>
 *   <li>X-Real-IP (nginx proxy header)</li>
 *   <li>Proxy-Client-IP (Apache proxy)</li>
 *   <li>WL-Proxy-Client-IP (WebLogic proxy)</li>
 *   <li>HTTP_X_FORWARDED_FOR (alternative header format)</li>
 *   <li>HTTP_CLIENT_IP (older proxy header)</li>
 *   <li>CF-Connecting-IP (Cloudflare CDN)</li>
 *   <li>True-Client-IP (Cloudflare Enterprise)</li>
 *   <li>X-Client-IP (various CDNs)</li>
 *   <li>Fastly-Client-IP (Fastly CDN)</li>
 *   <li>X-Cluster-Client-IP (various load balancers)</li>
 *   <li>X-Forwarded (RFC 7239)</li>
 *   <li>Forwarded-For (RFC 7239)</li>
 *   <li>Forwarded (RFC 7239 standard)</li>
 * </ul>
 * 
 * <p><b>Security Note:</b> This utility attempts to extract the real client IP
 * from proxy headers. In production, use {@code TrustedProxyResolver} with
 * hop-counting for secure IP resolution in multi-proxy environments.
 * 
 * @since 1.0.0
 */
public class IpAddressUtils {
    
    /**
     * All known proxy and CDN headers in order of preference.
     * Cloudflare headers have highest priority as they are most trusted.
     */
    private static final List<String> IP_HEADERS = Arrays.asList(
            // Cloudflare (highest priority - most trusted)
            "CF-Connecting-IP",
            "True-Client-IP",
            
            // Standard proxy headers
            "X-Forwarded-For",
            "X-Real-IP",
            
            // Alternative proxy headers
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            
            // CDN headers
            "X-Client-IP",
            "X-Forwarded",
            "Forwarded-For",
            "Forwarded",
            "X-Cluster-Client-IP",
            
            // Fastly CDN
            "Fastly-Client-IP",
            
            // Akamai
            "Akamai-Origin-Hop",
            
            // Other load balancers
            "X-Azure-ClientIP",
            "X-Azure-SocketIP"
    );
    
    /**
     * IPv4 pattern for validation.
     * Matches valid IPv4 addresses (0.0.0.0 to 255.255.255.255).
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );
    
    /**
     * IPv6 pattern for basic validation.
     * Matches common IPv6 address formats.
     */
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
            "^::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
            "^[0-9a-fA-F]{1,4}::(?:[0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4}$"
    );
    
    /**
     * Known private/internal IP ranges to filter out.
     */
    private static final List<String> PRIVATE_IP_PREFIXES = Arrays.asList(
            "10.",           // Class A private
            "172.16.",       // Class B private (172.16.0.0 - 172.31.255.255)
            "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.",      // Class C private
            "127.",          // Loopback
            "169.254.",      // Link-local
            "::1",           // IPv6 loopback
            "fc00:",         // IPv6 private
            "fd00:",         // IPv6 private
            "fe80:"          // IPv6 link-local
    );
    
    /**
     * Private constructor to prevent instantiation.
     */
    private IpAddressUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Get the client IP address from HTTP request.
     * 
     * <p>Checks multiple headers in order of preference and returns the first
     * valid public IP address found. Falls back to remote address if no valid
     * IP is found in headers.
     * 
     * @param request the HTTP request
     * @return the client IP address, or "unknown" if not determinable
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        // Try all headers in order of preference
        for (String header : IP_HEADERS) {
            String ip = extractIpFromHeader(request, header);
            if (ip != null && !ip.isEmpty()) {
                return ip;
            }
        }
        
        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isEmpty()) ? remoteAddr : "unknown";
    }
    
    /**
     * Extract the real client IP from a specific header.
     * Handles comma-separated lists (X-Forwarded-For chains).
     * 
     * @param request HTTP request
     * @param headerName header name to check
     * @return valid IP address or null
     */
    private static String extractIpFromHeader(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        
        if (headerValue == null || headerValue.isEmpty() || "unknown".equalsIgnoreCase(headerValue)) {
            return null;
        }
        
        // Handle comma-separated list (X-Forwarded-For: client, proxy1, proxy2)
        if (headerValue.contains(",")) {
            String[] ips = headerValue.split(",");
            
            // Try to find the first public (non-private) IP
            for (String ip : ips) {
                ip = ip.trim();
                if (isValidIpAddress(ip) && !isPrivateIpAddress(ip)) {
                    return ip;
                }
            }
            
            // If no public IP found, return the first valid IP
            for (String ip : ips) {
                ip = ip.trim();
                if (isValidIpAddress(ip)) {
                    return ip;
                }
            }
        }
        
        // Single IP address
        String ip = headerValue.trim();
        if (isValidIpAddress(ip)) {
            return ip;
        }
        
        return null;
    }
    
    /**
     * Validate IP address format (IPv4 or IPv6).
     * 
     * @param ip IP address string
     * @return true if valid IP format
     */
    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Check for "unknown" placeholder
        if ("unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        
        // Validate IPv4
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return isValidIpv4(ip);
        }
        
        // Validate IPv6
        if (IPV6_PATTERN.matcher(ip).find() || ip.contains(":")) {
            return isValidIpv6(ip);
        }
        
        return false;
    }
    
    /**
     * Validate IPv4 address.
     * 
     * @param ip the IPv4 address string
     * @return true if valid IPv4 address
     */
    private static boolean isValidIpv4(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate IPv6 address using InetAddress.
     * 
     * @param ip the IPv6 address string
     * @return true if valid IPv6 address
     */
    private static boolean isValidIpv6(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr instanceof java.net.Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Check if IP address is in private/internal range.
     * 
     * @param ip IP address
     * @return true if private IP
     */
    public static boolean isPrivateIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Check against known private IP prefixes
        for (String prefix : PRIVATE_IP_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get client IP with detailed information.
     * Useful for debugging and logging.
     * 
     * @param request HTTP request
     * @return IPInfo object with details
     */
    public static IpInfo getClientIpInfo(HttpServletRequest request) {
        if (request == null) {
            return new IpInfo("unknown", null, false, false);
        }
        
        // Try all headers
        for (String header : IP_HEADERS) {
            String ip = extractIpFromHeader(request, header);
            if (ip != null && !ip.isEmpty()) {
                boolean isPrivate = isPrivateIpAddress(ip);
                boolean isValid = isValidIpAddress(ip);
                return new IpInfo(ip, header, isValid, isPrivate);
            }
        }
        
        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        boolean isValid = isValidIpAddress(remoteAddr);
        boolean isPrivate = isPrivateIpAddress(remoteAddr);
        return new IpInfo(remoteAddr, "RemoteAddr", isValid, isPrivate);
    }
    
    /**
     * Get all IPs from the request (useful for debugging).
     * 
     * @param request HTTP request
     * @return list of all IP addresses found in various headers
     */
    public static List<String> getAllIpAddresses(HttpServletRequest request) {
        return IP_HEADERS.stream()
                .map(request::getHeader)
                .filter(header -> header != null && !header.isEmpty() && !"unknown".equalsIgnoreCase(header))
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Check if request is from a trusted proxy.
     * Configure trusted proxy IPs in your application.
     * 
     * @param request HTTP request
     * @param trustedProxies list of trusted proxy IP addresses
     * @return true if request is from trusted proxy
     */
    public static boolean isFromTrustedProxy(HttpServletRequest request, List<String> trustedProxies) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }
        
        String remoteAddr = request.getRemoteAddr();
        return trustedProxies.contains(remoteAddr);
    }
    
    /**
     * Get the real client IP considering trusted proxies.
     * 
     * @param request HTTP request
     * @param trustedProxies list of trusted proxy IPs
     * @return real client IP
     */
    public static String getClientIpWithTrustedProxies(HttpServletRequest request, List<String> trustedProxies) {
        // If not from trusted proxy, use remote address directly
        if (!isFromTrustedProxy(request, trustedProxies)) {
            return request.getRemoteAddr();
        }
        
        // From trusted proxy, check X-Forwarded-For
        return getClientIpAddress(request);
    }
    
    /**
     * IP address information container.
     * Provides detailed information about the resolved IP address.
     */
    public static class IpInfo {
        private final String ipAddress;
        private final String source;
        private final boolean valid;
        private final boolean privateIp;
        
        /**
         * Creates IP information.
         * 
         * @param ipAddress the IP address
         * @param source the source header or "RemoteAddr"
         * @param valid whether the IP is valid
         * @param privateIp whether the IP is in a private range
         */
        public IpInfo(String ipAddress, String source, boolean valid, boolean privateIp) {
            this.ipAddress = ipAddress;
            this.source = source;
            this.valid = valid;
            this.privateIp = privateIp;
        }
        
        /**
         * Gets the IP address.
         * 
         * @return the IP address
         */
        public String getIpAddress() {
            return ipAddress;
        }
        
        /**
         * Gets the source of the IP address.
         * 
         * @return the source header name or "RemoteAddr"
         */
        public String getSource() {
            return source;
        }
        
        /**
         * Checks if the IP address is valid.
         * 
         * @return true if valid
         */
        public boolean isValid() {
            return valid;
        }
        
        /**
         * Checks if the IP address is in a private range.
         * 
         * @return true if private
         */
        public boolean isPrivateIp() {
            return privateIp;
        }
        
        /**
         * Checks if the IP address is public (valid and not private).
         * 
         * @return true if public
         */
        public boolean isPublicIp() {
            return valid && !privateIp;
        }
        
        @Override
        public String toString() {
            return String.format("IP: %s, Source: %s, Valid: %s, Private: %s",
                    ipAddress, source, valid, privateIp);
        }
    }
}
