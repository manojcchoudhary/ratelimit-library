package com.lycosoft.ratelimit.example.standalone;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.engine.RateLimitContext;
import com.lycosoft.ratelimit.engine.RateLimitDecision;
import com.lycosoft.ratelimit.registry.LimiterRegistry;
import com.lycosoft.ratelimit.spi.AuditLogger;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spi.MetricsExporter;
import com.lycosoft.ratelimit.spi.NoOpMetricsExporter;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;

import java.util.concurrent.TimeUnit;

/**
 * Standalone example using LimiterEngine directly without any framework.
 * 
 * <p>Demonstrates:
 * <ul>
 *   <li>Direct engine usage</li>
 *   <li>Token bucket algorithm</li>
 *   <li>Processing loop with rate limiting</li>
 * </ul>
 * 
 * <p>Run with:
 * <pre>
 * mvn exec:java -Dexec.mainClass="com.lycosoft.ratelimit.example.standalone.StandaloneExample"
 * </pre>
 */
public class StandaloneExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(60));
        System.out.println("Standalone Rate Limiting Example");
        System.out.println("=".repeat(60));
        System.out.println();
        
        // 1. Create components
        StorageProvider storage = new InMemoryStorageProvider();
        KeyResolver keyResolver = context -> context.getKeyExpression(); // Identity
        MetricsExporter metrics = new NoOpMetricsExporter();
        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void logConfigChange(ConfigChangeEvent event) {}
            
            @Override
            public void logEnforcementAction(EnforcementEvent event) {}
            
            @Override
            public void logSystemFailure(SystemFailureEvent event) {}
        };
        
        // 2. Create engine
        LimiterEngine engine = new LimiterEngine(storage, keyResolver, metrics, auditLogger);
        
        // 3. Define rate limit (Token Bucket)
        RateLimitConfig config = RateLimitConfig.builder()
            .name("processing-loop")
            .algorithm("TOKEN_BUCKET")
            .requests(10)            // Base limit
            .window(1)               // Per second
            .capacity(50)            // Burst capacity
            .refillRate(10)          // 10 tokens/second
            .build();
        
        // 4. Simulate processing loop
        System.out.println("Starting processing loop...");
        System.out.println("Bucket capacity: 50, Refill: 10/second");
        System.out.println();
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("task-processor")
            .build();
        
        for (int i = 1; i <= 100; i++) {
            RateLimitDecision decision = engine.checkLimit("task-processor", config, context);
            
            if (decision.isAllowed()) {
                System.out.printf("Task #%03d: ✓ PROCESSED (remaining: %d tokens)%n", 
                    i, decision.getRemaining());
                
                // Simulate processing
                processTask(i);
            } else {
                System.out.printf("Task #%03d: ✗ THROTTLED (wait %d seconds, retry...)%n", 
                    i, decision.getRetryAfterSeconds());
                
                // Wait and retry
                TimeUnit.SECONDS.sleep(decision.getRetryAfterSeconds());
                i--; // Retry same task
            }
        }
        
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Processing complete!");
        System.out.println("=".repeat(60));
    }
    
    private static void processTask(int taskId) {
        // Simulate work
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
