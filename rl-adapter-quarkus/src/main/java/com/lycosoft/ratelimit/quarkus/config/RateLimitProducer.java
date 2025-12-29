package com.lycosoft.ratelimit.quarkus.config;

import com.lycosoft.ratelimit.engine.LimiterEngine;
import com.lycosoft.ratelimit.network.AdaptiveThrottler;
import com.lycosoft.ratelimit.network.TrustedProxyResolver;
import com.lycosoft.ratelimit.spi.AuditLogger;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.spi.MetricsExporter;
import com.lycosoft.ratelimit.spi.NoOpMetricsExporter;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import com.lycosoft.ratelimit.storage.StaticKeyResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/**
 * CDI producer for rate limiting beans in Quarkus.
 * 
 * <p>This producer creates all necessary beans if they don't already exist.
 * Applications can override any bean by providing their own {@code @Produces} method.
 * 
 * @since 1.0.0
 */
@ApplicationScoped
public class RateLimitProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitProducer.class);
    
    /**
     * Produces a default storage provider (in-memory).
     * 
     * <p>Override this by providing your own {@code StorageProvider} bean.
     * 
     * @return the in-memory storage provider
     */
    @Produces
    @Singleton
    public StorageProvider produceStorageProvider() {
        logger.info("Creating default InMemoryStorageProvider");
        logger.warn("Using in-memory storage - not suitable for clustered deployments");
        return new InMemoryStorageProvider();
    }
    
    /**
     * Produces a simple key resolver.
     * 
     * @return the key resolver
     */
    @Produces
    @Singleton
    public KeyResolver produceKeyResolver() {
        logger.info("Creating StaticKeyResolver");
        return new StaticKeyResolver();
    }
    
    /**
     * Produces a metrics exporter.
     * 
     * <p>Defaults to no-op. Applications can override this by providing
     * their own {@code @Produces MetricsExporter} bean.
     * 
     * @return the metrics exporter
     */
    @Produces
    @Singleton
    public MetricsExporter produceMetricsExporter() {
        logger.info("Creating NoOpMetricsExporter (override with custom @Produces if needed)");
        return new NoOpMetricsExporter();
    }
    
    /**
     * Produces a no-op audit logger.
     * 
     * @return the audit logger
     */
    @Produces
    @Singleton
    public AuditLogger produceAuditLogger() {
        logger.info("Creating NoOpAuditLogger");
        return new AuditLogger() {
            @Override
            public void logConfigChange(ConfigChangeEvent event) {}
            
            @Override
            public void logEnforcementAction(EnforcementEvent event) {}
            
            @Override
            public void logSystemFailure(SystemFailureEvent event) {}
        };
    }
    
    /**
     * Produces the limiter engine.
     * 
     * @param storageProvider the storage provider
     * @param keyResolver the key resolver
     * @param metricsExporter the metrics exporter
     * @param auditLogger the audit logger
     * @return the limiter engine
     */
    @Produces
    @Singleton
    public LimiterEngine produceLimiterEngine(StorageProvider storageProvider,
                                             KeyResolver keyResolver,
                                             MetricsExporter metricsExporter,
                                             AuditLogger auditLogger) {
        logger.info("Creating LimiterEngine");
        return new LimiterEngine(storageProvider, keyResolver, metricsExporter, auditLogger);
    }
    
    /**
     * Creates a trusted proxy resolver for hop-counting IP resolution.
     * 
     * @param trustedHops number of trusted hops (default: 1)
     * @param trustedProxies comma-separated CIDR ranges (default: localhost)
     * @return the trusted proxy resolver
     * @since 1.1.0
     */
    @Produces
    @Singleton
    public TrustedProxyResolver produceTrustedProxyResolver(
            @ConfigProperty(name = "ratelimit.proxy.trusted-hops", defaultValue = "1") int trustedHops,
            @ConfigProperty(name = "ratelimit.proxy.trusted-proxies", 
                           defaultValue = "127.0.0.0/8,::1/128") Optional<String> trustedProxies) {
        
        Set<String> proxySet = trustedProxies
            .map(s -> Set.of(s.split(",")))
            .orElse(Set.of("127.0.0.0/8", "::1/128"));
        
        logger.info("Creating TrustedProxyResolver: hops={}, proxies={}", 
                   trustedHops, proxySet.size());
        return new TrustedProxyResolver(trustedHops, proxySet);
    }
    
    /**
     * Creates an adaptive throttler if enabled, or a no-op throttler if disabled.
     *
     * <p><b>Note:</b> When disabled, returns a no-op throttler (maxDelayMs=0) instead of null
     * to prevent NullPointerExceptions in consumers that inject AdaptiveThrottler directly.
     *
     * @param enabled whether adaptive throttling is enabled
     * @param softLimit soft limit threshold (default: 80)
     * @param maxDelayMs maximum delay in milliseconds (default: 2000)
     * @param strategy throttling strategy (default: LINEAR)
     * @return the adaptive throttler (never null)
     * @since 1.1.0
     */
    @Produces
    @Singleton
    public AdaptiveThrottler produceAdaptiveThrottler(
            @ConfigProperty(name = "ratelimit.throttling.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "ratelimit.throttling.soft-limit", defaultValue = "80") int softLimit,
            @ConfigProperty(name = "ratelimit.throttling.max-delay-ms", defaultValue = "2000") long maxDelayMs,
            @ConfigProperty(name = "ratelimit.throttling.strategy", defaultValue = "LINEAR") String strategy) {

        if (!enabled) {
            logger.info("Adaptive throttling is disabled, creating no-op throttler");
            // Return a no-op throttler (maxDelayMs=0 means calculateDelay always returns 0)
            return new AdaptiveThrottler(100, 100, 0, AdaptiveThrottler.Strategy.LINEAR);
        }

        logger.info("Creating AdaptiveThrottler: softLimit={}, maxDelay={}ms, strategy={}",
                   softLimit, maxDelayMs, strategy);

        AdaptiveThrottler.Strategy strategyEnum = AdaptiveThrottler.Strategy.valueOf(
            strategy.toUpperCase()
        );

        return new AdaptiveThrottler(
            softLimit,
            100,  // Hard limit (100%)
            maxDelayMs,
            strategyEnum
        );
    }
}
