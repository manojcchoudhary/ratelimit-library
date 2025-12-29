package com.lycosoft.ratelimit.example.springboot.controller;

import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Public API Controller - Scenario 1: IP-Based Fixed Window.
 * 
 * <p><b>Goal:</b> Protect public endpoints from abuse using client IP address.
 * 
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>Algorithm: Fixed Window (default)</li>
 *   <li>Key: Client IP address (#ip)</li>
 *   <li>Limit: 10 requests per 60 seconds</li>
 * </ul>
 * 
 * <p><b>Use Case:</b> Public API endpoints that don't require authentication
 * but need protection from excessive requests.
 * 
 * <p><b>Testing:</b>
 * <pre>
 * # Normal request (will succeed first 10 times)
 * curl http://localhost:8080/api/public/data
 * 
 * # After 10 requests within 60 seconds
 * HTTP/1.1 429 Too Many Requests
 * Retry-After: 45
 * X-RateLimit-Limit: 10
 * X-RateLimit-Remaining: 0
 * X-RateLimit-Reset: 1703635200
 * 
 * # From different IP (new limit)
 * curl --interface eth1 http://localhost:8080/api/public/data
 * </pre>
 */
@RestController
@RequestMapping("/api/public")
public class PublicApiController {
    
    /**
     * Get public data with IP-based rate limiting.
     * 
     * <p>Rate Limit: 10 requests per 60 seconds per IP address.
     * 
     * @return public data
     */
    @RateLimit(
        name = "public-api",
        key = "#ip",                    // Use client IP as key
        requests = 10,                  // 10 requests
        window = 60                     // per 60 seconds
    )
    @GetMapping("/data")
    public Map<String, Object> getPublicData() {
        return Map.of(
            "message", "Public data retrieved successfully",
            "timestamp", LocalDateTime.now(),
            "data", generateSampleData()
        );
    }
    
    /**
     * Search endpoint with stricter limits.
     * 
     * <p>Rate Limit: 5 requests per 60 seconds per IP address.
     * Search operations are more expensive, so we apply stricter limits.
     * 
     * @return search results
     */
    @RateLimit(
        name = "public-search",
        key = "#ip",
        requests = 5,                   // Only 5 requests
        window = 60
    )
    @GetMapping("/search")
    public Map<String, Object> search() {
        return Map.of(
            "message", "Search completed",
            "timestamp", LocalDateTime.now(),
            "results", generateSearchResults()
        );
    }
    
    /**
     * Health check endpoint - no rate limiting.
     * 
     * <p>Health checks should not be rate limited as they're used by
     * load balancers and monitoring systems.
     * 
     * @return health status
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now().toString()
        );
    }
    
    private Object generateSampleData() {
        return Map.of(
            "id", 123,
            "value", "Sample public data",
            "category", "general"
        );
    }
    
    private Object generateSearchResults() {
        return java.util.List.of(
            Map.of("id", 1, "title", "Result 1"),
            Map.of("id", 2, "title", "Result 2"),
            Map.of("id", 3, "title", "Result 3")
        );
    }
}
