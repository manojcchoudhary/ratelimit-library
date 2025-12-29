package com.lycosoft.ratelimit.example.springboot.controller;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spring.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Partner API Controller - Scenario 3: Token Bucket for API Partners.
 * 
 * <p><b>Goal:</b> Allow burst traffic for partner integrations while
 * maintaining average rate limits.
 * 
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>Algorithm: Token Bucket</li>
 *   <li>Key: Partner ID from header (#headers['X-Partner-ID'])</li>
 *   <li>Capacity: 100 tokens (burst)</li>
 *   <li>Refill Rate: 10 tokens/second (600/minute sustained)</li>
 * </ul>
 * 
 * <p><b>Token Bucket Behavior:</b>
 * <ul>
 *   <li>Bucket starts full (100 tokens)</li>
 *   <li>Each request consumes 1 token</li>
 *   <li>Tokens refill at 10/second</li>
 *   <li>Allows burst of 100 requests, then sustained 10/second</li>
 * </ul>
 * 
 * <p><b>Use Case:</b> Partner APIs where you want to allow occasional
 * bursts (e.g., batch processing) but limit sustained rate.
 * 
 * <p><b>Testing:</b>
 * <pre>
 * # Single request (consumes 1 token, 99 remaining)
 * curl -H "X-Partner-ID: partner-123" http://localhost:8080/api/partner/events
 * 
 * # Burst 100 requests (bucket empties)
 * for i in {1..100}; do
 *   curl -H "X-Partner-ID: partner-123" http://localhost:8080/api/partner/events
 * done
 * 
 * # 101st request (no tokens available)
 * HTTP/1.1 429 Too Many Requests
 * 
 * # Wait 10 seconds (100 tokens refilled)
 * sleep 10
 * curl -H "X-Partner-ID: partner-123" http://localhost:8080/api/partner/events
 * # Success! Bucket refilled
 * </pre>
 */
@RestController
@RequestMapping("/api/partner")
public class PartnerApiController {
    
    /**
     * Partner events endpoint with token bucket rate limiting.
     * 
     * <p>Token bucket allows burst traffic while maintaining average rate.
     * Perfect for partner integrations that may send batches of events.
     * 
     * @param event the event data
     * @return event processing confirmation
     */
    @RateLimit(
        name = "partner-events",
        algorithm = RateLimitConfig.Algorithm.TOKEN_BUCKET,
        key = "#headers['X-Partner-ID']",
        capacity = 100,              // Bucket capacity (max burst)
        refillRate = 10              // Tokens per second (sustained rate)
    )
    @PostMapping("/events")
    public Map<String, Object> receiveEvent(@RequestBody Map<String, Object> event) {
        return Map.of(
            "message", "Event received successfully",
            "eventId", java.util.UUID.randomUUID().toString(),
            "timestamp", LocalDateTime.now(),
            "processed", true
        );
    }
    
    /**
     * Batch events endpoint with larger token bucket.
     * 
     * <p>Allows larger bursts for batch processing scenarios.
     * 
     * @param events list of events
     * @return batch processing confirmation
     */
    @RateLimit(
        name = "partner-batch",
        algorithm = RateLimitConfig.Algorithm.TOKEN_BUCKET,
        key = "#headers['X-Partner-ID']",
        capacity = 500,              // Larger capacity for batches
        refillRate = 50              // Higher refill rate
    )
    @PostMapping("/events/batch")
    public Map<String, Object> receiveBatch(@RequestBody java.util.List<Map<String, Object>> events) {
        return Map.of(
            "message", "Batch processed successfully",
            "batchId", java.util.UUID.randomUUID().toString(),
            "eventsProcessed", events.size(),
            "timestamp", LocalDateTime.now()
        );
    }
    
    /**
     * Partner analytics endpoint with fixed window.
     * 
     * <p>Analytics queries are read-heavy, so we use fixed window
     * instead of token bucket.
     * 
     * @return analytics data
     */
    @RateLimit(
        name = "partner-analytics",
        key = "#headers['X-Partner-ID']",
        requests = 100,
        window = 3600                // 100 requests per hour
    )
    @GetMapping("/analytics")
    public Map<String, Object> getAnalytics() {
        return Map.of(
            "message", "Analytics data retrieved",
            "timestamp", LocalDateTime.now(),
            "metrics", generateAnalytics()
        );
    }
    
    /**
     * Partner webhook registration with strict rate limiting.
     * 
     * <p>Webhook registration is a write operation that should be
     * rate limited strictly to prevent abuse.
     * 
     * @param webhook webhook configuration
     * @return registration confirmation
     */
    @RateLimit(
        name = "partner-webhook-register",
        key = "#headers['X-Partner-ID']",
        requests = 5,                // Only 5 registrations
        window = 3600                // per hour
    )
    @PostMapping("/webhooks")
    public Map<String, Object> registerWebhook(@RequestBody Map<String, Object> webhook) {
        return Map.of(
            "message", "Webhook registered successfully",
            "webhookId", java.util.UUID.randomUUID().toString(),
            "url", webhook.get("url"),
            "timestamp", LocalDateTime.now()
        );
    }
    
    private Object generateAnalytics() {
        return Map.of(
            "totalEvents", 12345,
            "eventsToday", 456,
            "avgProcessingTime", "45ms",
            "successRate", "99.8%"
        );
    }
}
