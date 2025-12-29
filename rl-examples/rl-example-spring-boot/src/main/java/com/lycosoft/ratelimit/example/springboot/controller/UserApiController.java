package com.lycosoft.ratelimit.example.springboot.controller;

import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import com.lycosoft.ratelimit.spring.annotation.RateLimits;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * User API Controller - Scenario 2: User-Tiered Sliding Window.
 * 
 * <p><b>Goal:</b> Apply different rate limits based on user subscription tier.
 * 
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>Algorithm: Sliding Window Counter</li>
 *   <li>Key: User ID with tier (#principal.username + ':' + #principal.authorities[0])</li>
 *   <li>BASIC users: 100 requests/hour</li>
 *   <li>PREMIUM users: 1000 requests/hour</li>
 * </ul>
 * 
 * <p><b>Use Case:</b> SaaS applications with tiered pricing where different
 * subscription levels get different API quotas.
 * 
 * <p><b>Testing:</b>
 * <pre>
 * # BASIC user (100 req/hour limit)
 * curl -u basic:password http://localhost:8080/api/user/profile
 * 
 * # PREMIUM user (1000 req/hour limit)
 * curl -u premium:password http://localhost:8080/api/user/dashboard
 * 
 * # Check rate limit headers
 * curl -v -u basic:password http://localhost:8080/api/user/profile
 * X-RateLimit-Limit: 100
 * X-RateLimit-Remaining: 99
 * </pre>
 */
@RestController
@RequestMapping("/api/user")
public class UserApiController {
    
    /**
     * Get user profile with sliding window rate limiting.
     * 
     * <p>Uses sliding window algorithm for more accurate rate limiting
     * compared to fixed window.
     * 
     * <p>Rate limits are applied per user, not per IP, to handle users
     * behind NAT or proxies correctly.
     * 
     * @param user the authenticated user
     * @return user profile
     */
    @RateLimits({
        @RateLimit(
            name = "user-profile-basic",
            algorithm = com.lycosoft.ratelimit.config.RateLimitConfig.Algorithm.SLIDING_WINDOW,
            key = "'user:' + #principal.username + ':BASIC'",
            requests = 100,              // BASIC: 100 requests
            window = 3600                // per hour
        ),
        @RateLimit(
            name = "user-profile-premium",
            algorithm = com.lycosoft.ratelimit.config.RateLimitConfig.Algorithm.SLIDING_WINDOW,
            key = "'user:' + #principal.username + ':PREMIUM'",
            requests = 1000,             // PREMIUM: 1000 requests
            window = 3600                // per hour
        )
    })
    @GetMapping("/profile")
    public Map<String, Object> getUserProfile(@AuthenticationPrincipal UserDetails user) {
        return Map.of(
            "username", user.getUsername(),
            "authorities", user.getAuthorities(),
            "timestamp", LocalDateTime.now(),
            "profile", getUserData(user.getUsername())
        );
    }
    
    /**
     * User dashboard with tier-aware rate limiting.
     * 
     * <p>This endpoint demonstrates using SpEL to dynamically determine
     * the rate limit based on user attributes.
     * 
     * @param user the authenticated user
     * @return dashboard data
     */
    @RateLimit(
        name = "user-dashboard",
        algorithm = com.lycosoft.ratelimit.config.RateLimitConfig.Algorithm.SLIDING_WINDOW,
        key = "'dashboard:' + #principal.username",
        requests = 50,
        window = 60
    )
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(@AuthenticationPrincipal UserDetails user) {
        String tier = user.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("BASIC");
        
        return Map.of(
            "username", user.getUsername(),
            "tier", tier,
            "timestamp", LocalDateTime.now(),
            "widgets", generateDashboardWidgets(tier)
        );
    }
    
    /**
     * Update user settings with combined IP and user rate limiting.
     * 
     * <p>Applies both per-IP and per-user limits to prevent abuse.
     * 
     * @param user the authenticated user
     * @param settings the settings to update
     * @return update confirmation
     */
    @RateLimits({
        @RateLimit(
            name = "update-settings-per-ip",
            key = "#ip",
            requests = 20,
            window = 60
        ),
        @RateLimit(
            name = "update-settings-per-user",
            key = "'settings:' + #principal.username",
            requests = 10,
            window = 60
        )
    })
    @PostMapping("/settings")
    public Map<String, Object> updateSettings(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Object> settings) {
        
        return Map.of(
            "message", "Settings updated successfully",
            "username", user.getUsername(),
            "timestamp", LocalDateTime.now(),
            "updated", settings.keySet()
        );
    }
    
    private Object getUserData(String username) {
        return Map.of(
            "email", username + "@example.com",
            "name", "User " + username,
            "joined", "2024-01-01"
        );
    }
    
    private Object generateDashboardWidgets(String tier) {
        return "PREMIUM".equals(tier) 
            ? java.util.List.of("Analytics", "Reports", "Advanced Metrics", "Custom Dashboards")
            : java.util.List.of("Basic Stats", "Simple Reports");
    }
}
