package com.lycosoft.ratelimit.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Rate Limiting Example Application.
 * 
 * <p>Demonstrates:
 * <ul>
 *   <li>IP-based fixed window rate limiting</li>
 *   <li>User-tiered sliding window rate limiting</li>
 *   <li>Token bucket for API partners</li>
 *   <li>Different storage strategies (in-memory, Redis)</li>
 *   <li>Resilient fail-open configuration</li>
 *   <li>Metrics and monitoring with Prometheus</li>
 * </ul>
 * 
 * <p><b>Running the example:</b>
 * <pre>
 * # With in-memory storage (default)
 * mvn spring-boot:run
 * 
 * # With Redis
 * mvn spring-boot:run -Dspring.profiles.active=redis
 * 
 * # With resilient fail-open
 * mvn spring-boot:run -Dspring.profiles.active=resilient
 * </pre>
 * 
 * <p><b>Test endpoints:</b>
 * <pre>
 * # Public endpoint (IP-based)
 * curl http://localhost:8080/api/public/data
 * 
 * # User endpoint (tier-based)
 * curl -u basic:password http://localhost:8080/api/user/profile
 * curl -u premium:password http://localhost:8080/api/user/dashboard
 * 
 * # Partner API (token bucket)
 * curl -H "X-Partner-ID: partner-123" http://localhost:8080/api/partner/events
 * 
 * # Metrics
 * curl http://localhost:8080/actuator/prometheus
 * </pre>
 */
@SpringBootApplication
public class RateLimitExampleApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RateLimitExampleApplication.class, args);
    }
}
