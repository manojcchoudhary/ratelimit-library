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

/**
 * Example of custom key resolution from business objects.
 * 
 * <p>Demonstrates:
 * <ul>
 *   <li>Custom KeyResolver implementation</li>
 *   <li>Extracting keys from POJOs</li>
 *   <li>Per-customer rate limiting</li>
 * </ul>
 */
public class CustomKeyResolverExample {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Custom Key Resolver Example");
        System.out.println("=".repeat(60));
        System.out.println();
        
        // Custom key resolver for business objects
        KeyResolver keyResolver = new BusinessObjectKeyResolver();
        
        StorageProvider storage = new InMemoryStorageProvider();
        MetricsExporter metrics = new NoOpMetricsExporter();
        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void logConfigChange(ConfigChangeEvent event) {}
            
            @Override
            public void logEnforcementAction(EnforcementEvent event) {}
            
            @Override
            public void logSystemFailure(SystemFailureEvent event) {}
        };
        
        LimiterEngine engine = new LimiterEngine(storage, keyResolver, metrics, auditLogger);
        
        // Create rate limit config
        RateLimitConfig config = RateLimitConfig.builder()
            .name("order-processing")
            .requests(5)
            .window(60)
            .build();
        
        // Process orders for different customers
        System.out.println("Processing orders (limit: 5 per customer per minute)...");
        System.out.println();
        
        for (int i = 1; i <= 10; i++) {
            Order order = new Order("customer-123", "order-" + i, 100.0 * i);
            
            // Build context with order data
            RateLimitContext context = RateLimitContext.builder()
                .keyExpression("customer:" + order.getCustomerId())
                .build();
            
            String key = keyResolver.resolveKey(context);
            RateLimitDecision decision = engine.checkLimit(key, config, context);
            
            if (decision.isAllowed()) {
                System.out.printf("Order %s: ✓ ACCEPTED (remaining: %d)%n", 
                    order.getOrderId(), decision.getRemaining());
            } else {
                System.out.printf("Order %s: ✗ REJECTED (customer rate limit exceeded)%n", 
                    order.getOrderId());
            }
        }
        
        System.out.println();
        
        // Try different customer
        System.out.println("Trying different customer...");
        Order order = new Order("customer-456", "order-new-1", 500.0);
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("customer:" + order.getCustomerId())
            .build();
        
        String key = keyResolver.resolveKey(context);
        RateLimitDecision decision = engine.checkLimit(key, config, context);
        
        if (decision.isAllowed()) {
            System.out.printf("Order %s: ✓ ACCEPTED (new customer, remaining: %d)%n", 
                order.getOrderId(), decision.getRemaining());
        }
        
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Example complete!");
        System.out.println("=".repeat(60));
    }
    
    /**
     * Custom key resolver that extracts keys from business context.
     */
    static class BusinessObjectKeyResolver implements KeyResolver {
        @Override
        public String resolveKey(RateLimitContext context) {
            // In real scenario, might extract from request attributes,
            // session data, or custom context fields
            return context.getKeyExpression();
        }
    }
    
    /**
     * Business object representing an order.
     */
    static class Order {
        private final String customerId;
        private final String orderId;
        private final double amount;
        
        public Order(String customerId, String orderId, double amount) {
            this.customerId = customerId;
            this.orderId = orderId;
            this.amount = amount;
        }
        
        public String getCustomerId() { return customerId; }
        public String getOrderId() { return orderId; }
        public double getAmount() { return amount; }
    }
}
