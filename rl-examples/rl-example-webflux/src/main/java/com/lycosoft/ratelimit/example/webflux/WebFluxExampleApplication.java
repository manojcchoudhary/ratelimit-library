package com.lycosoft.ratelimit.example.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring WebFlux example demonstrating reactive rate limiting.
 * 
 * <p>Features:
 * <ul>
 *   <li>Non-blocking rate limit checks</li>
 *   <li>Functional routing with rate limits</li>
 *   <li>WebFilter-based rate limiting</li>
 * </ul>
 * 
 * <p>Run with:
 * <pre>
 * mvn spring-boot:run
 * </pre>
 * 
 * <p>Test endpoints:
 * <ul>
 *   <li>http://localhost:8081/reactive/data - Rate limited data endpoint</li>
 *   <li>http://localhost:8081/reactive/stream - Streaming endpoint</li>
 * </ul>
 */
@SpringBootApplication
public class WebFluxExampleApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(WebFluxExampleApplication.class, args);
    }
}
