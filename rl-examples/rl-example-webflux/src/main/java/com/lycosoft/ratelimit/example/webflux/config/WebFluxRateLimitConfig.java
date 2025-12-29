package com.lycosoft.ratelimit.example.webflux.config;

import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.registry.LimiterRegistry;
import com.lycosoft.ratelimit.spi.AuditLogger;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spi.MetricsExporter;
import com.lycosoft.ratelimit.spi.NoOpMetricsExporter;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebFlux-specific rate limit configuration.
 */
@Configuration
public class WebFluxRateLimitConfig {
    
    @Bean
    public StorageProvider storageProvider() {
        return new InMemoryStorageProvider();
    }
    
    @Bean
    public KeyResolver keyResolver() {
        return context -> context.getKeyExpression();
    }
    
    @Bean
    public MetricsExporter metricsExporter() {
        return new NoOpMetricsExporter();
    }
    
    @Bean
    public AuditLogger auditLogger() {
        return new AuditLogger() {
            @Override
            public void logConfigChange(ConfigChangeEvent event) {}
            
            @Override
            public void logEnforcementAction(EnforcementEvent event) {}
            
            @Override
            public void logSystemFailure(SystemFailureEvent event) {}
        };
    }
    
    @Bean
    public LimiterEngine limiterEngine(
            StorageProvider storageProvider,
            KeyResolver keyResolver,
            MetricsExporter metricsExporter,
            AuditLogger auditLogger) {
        return new LimiterEngine(storageProvider, keyResolver, metricsExporter, auditLogger);
    }
}
