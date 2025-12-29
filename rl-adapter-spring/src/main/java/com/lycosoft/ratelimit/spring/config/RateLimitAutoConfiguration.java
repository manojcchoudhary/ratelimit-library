package com.lycosoft.ratelimit.spring.config;

import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.network.AdaptiveThrottler;
import com.lycosoft.ratelimit.network.TrustedProxyResolver;
import com.lycosoft.ratelimit.registry.LimiterRegistry;
import com.lycosoft.ratelimit.spi.AuditLogger;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spi.MetricsExporter;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.spring.aop.RateLimitAspect;
import com.lycosoft.ratelimit.spring.handler.RateLimitExceptionHandler;
import com.lycosoft.ratelimit.spring.metrics.MicrometerMetricsExporter;
import com.lycosoft.ratelimit.spring.resolver.OptimizedSpELKeyResolver;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring Boot auto-configuration for rate limiting.
 * 
 * <p>This configuration automatically sets up:
 * <ul>
 *   <li>Storage provider (defaults to in-memory)</li>
 *   <li>Key resolver (optimized SpEL)</li>
 *   <li>Metrics exporter (Micrometer)</li>
 *   <li>Limiter engine</li>
 *   <li>AOP aspect</li>
 * </ul>
 * 
 * <p><b>Customization:</b>
 * Any bean can be overridden by providing your own {@code @Bean} definition.
 * 
 * <p><b>Configuration Properties:</b>
 * See {@link RateLimitProperties} for available configuration options.
 * 
 * @since 1.0.0
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitAutoConfiguration.class);
    
    /**
     * Creates a default storage provider (in-memory).
     * 
     * <p>This bean is only created if no other {@link StorageProvider} bean exists.
     * To use Redis or Caffeine, provide your own {@code StorageProvider} bean.
     * 
     * @return the in-memory storage provider
     */
    @Bean
    @ConditionalOnMissingBean
    public StorageProvider storageProvider() {
        logger.info("Creating default InMemoryStorageProvider");
        logger.warn("Using in-memory storage - not suitable for clustered deployments. " +
                   "Configure Redis or Caffeine for production.");
        return new InMemoryStorageProvider();
    }
    
    /**
     * Creates an optimized SpEL key resolver.
     * 
     * <p>This resolver provides:
     * <ul>
     *   <li>Compiled SpEL expressions (40× faster)</li>
     *   <li>Expression caching</li>
     *   <li>Static key fast-path</li>
     * </ul>
     * 
     * @param properties the rate limit properties
     * @return the optimized SpEL key resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public KeyResolver keyResolver(RateLimitProperties properties) {
        logger.info("Creating OptimizedSpELKeyResolver with cache size: {}", 
                   properties.getSpel().getCacheSize());
        return new OptimizedSpELKeyResolver(
            properties.getSpel().getCompilerMode(),
            properties.getSpel().getCacheSize()
        );
    }
    
    /**
     * Creates a trusted proxy resolver for hop-counting IP resolution.
     * 
     * @param properties the rate limit properties
     * @return the trusted proxy resolver
     * @since 1.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    public TrustedProxyResolver trustedProxyResolver(RateLimitProperties properties) {
        logger.info("Creating TrustedProxyResolver: hops={}, proxies={}", 
                   properties.getProxy().getTrustedHops(),
                   properties.getProxy().getTrustedProxies().size());
        return new TrustedProxyResolver(
            properties.getProxy().getTrustedHops(),
            properties.getProxy().getTrustedProxies()
        );
    }
    
    /**
     * Creates an adaptive throttler if enabled.
     * 
     * @param properties the rate limit properties
     * @return the adaptive throttler, or null if disabled
     * @since 1.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ratelimit.throttling", name = "enabled", havingValue = "true")
    public AdaptiveThrottler adaptiveThrottler(RateLimitProperties properties) {
        var config = properties.getThrottling();
        logger.info("Creating AdaptiveThrottler: softLimit={}, maxDelay={}ms, strategy={}", 
                   config.getSoftLimit(), config.getMaxDelayMs(), config.getStrategy());
        
        AdaptiveThrottler.Strategy strategy = AdaptiveThrottler.Strategy.valueOf(
            config.getStrategy().toUpperCase()
        );
        
        return new AdaptiveThrottler(
            config.getSoftLimit(),
            100,  // Hard limit (100%)
            config.getMaxDelayMs(),
            strategy
        );
    }
    
    /**
     * Creates a Micrometer metrics exporter.
     * 
     * @param meterRegistry the Micrometer meter registry
     * @return the metrics exporter
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsExporter metricsExporter(MeterRegistry meterRegistry) {
        logger.info("Creating MicrometerMetricsExporter");
        return new MicrometerMetricsExporter(meterRegistry);
    }
    
    /**
     * Creates the limiter registry.
     * 
     * @return the limiter registry
     */
    @Bean
    @ConditionalOnMissingBean
    public LimiterRegistry limiterRegistry() {
        logger.info("Creating LimiterRegistry");
        return new LimiterRegistry();
    }
    
    /**
     * Creates a no-op audit logger bean.
     * 
     * <p>Applications can override this to provide custom audit logging.
     * 
     * @return the audit logger
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogger auditLogger() {
        logger.info("Creating NoOp AuditLogger (override for custom audit logging)");
        return new AuditLogger() {
            @Override
            public void logConfigChange(ConfigChangeEvent event) {
                // No-op implementation
            }
            
            @Override
            public void logEnforcementAction(EnforcementEvent event) {
                // No-op implementation
            }
            
            @Override
            public void logSystemFailure(SystemFailureEvent event) {
                // No-op implementation
            }
        };
    }
    
    /**
     * Creates the limiter engine.
     * 
     * @param storageProvider the storage provider
     * @param keyResolver the key resolver
     * @param metricsExporter the metrics exporter
     * @param auditLogger the audit logger
     * @return the limiter engine
     */
    @Bean
    @ConditionalOnMissingBean
    public LimiterEngine limiterEngine(StorageProvider storageProvider,
                                      KeyResolver keyResolver,
                                      MetricsExporter metricsExporter,
                                      AuditLogger auditLogger) {
        logger.info("Creating LimiterEngine");
        return new LimiterEngine(storageProvider, keyResolver, metricsExporter, auditLogger);
    }
    
    /**
     * Creates the AOP aspect for {@code @RateLimit} annotation.
     * 
     * @param limiterEngine the limiter engine
     * @param keyResolver the key resolver
     * @param proxyResolver the trusted proxy resolver
     * @param adaptiveThrottler the adaptive throttler (optional)
     * @return the rate limit aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(LimiterEngine limiterEngine, 
                                          KeyResolver keyResolver,
                                          TrustedProxyResolver proxyResolver,
                                          @org.springframework.beans.factory.annotation.Autowired(required = false) 
                                          AdaptiveThrottler adaptiveThrottler) {
        logger.info("Creating RateLimitAspect with proxy resolver and adaptive throttling");
        return new RateLimitAspect(limiterEngine, keyResolver, proxyResolver, adaptiveThrottler);
    }
    
    /**
     * Creates the RFC 9457 exception handler.
     * 
     * @return the exception handler
     * @since 1.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitExceptionHandler rateLimitExceptionHandler() {
        logger.info("Creating RateLimitExceptionHandler for RFC 9457 support");
        return new RateLimitExceptionHandler();
    }
}
