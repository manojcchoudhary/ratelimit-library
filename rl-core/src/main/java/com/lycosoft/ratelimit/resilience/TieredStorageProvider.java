package com.lycosoft.ratelimit.resilience;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Tiered storage provider with L1/L2 failover strategy.
 *
 * <p>This implementation provides resilient rate limiting by combining:
 * <ul>
 *   <li><b>L1 (Primary):</b> Distributed storage (e.g., Redis) for cluster-wide consistency</li>
 *   <li><b>L2 (Fallback):</b> Local storage (e.g., Caffeine) for per-node protection during outages</li>
 * </ul>
 *
 * <p><b>Behavior:</b>
 * <ul>
 *   <li><b>Normal Operation:</b> All requests go to L1 (CP mode - Consistency + Partition tolerance)</li>
 *   <li><b>L1 Failure:</b> Circuit breaker trips, requests go to L2 (AP mode - Availability + Partition tolerance)</li>
 *   <li><b>L1 Recovery:</b> Jittered reconnection prevents thundering herd, L2 state discarded</li>
 * </ul>
 *
 * <p><b>CAP Theorem Trade-off:</b>
 * When L1 fails and L2 is active, total cluster traffic may exceed the global limit by:
 * {@code (Node_Count - 1) × Limit}
 *
 * <p>This is an acceptable trade-off for service availability. Business stakeholders must
 * sign off on this behavior for critical endpoints.
 *
 * @since 1.0.0
 */
public class TieredStorageProvider implements StorageProvider {

    private static final Logger logger = LoggerFactory.getLogger(TieredStorageProvider.class);

    private final StorageProvider l1Provider;  // Primary (e.g., Redis)
    private final StorageProvider l2Provider;  // Fallback (e.g., Caffeine)
    private final JitteredCircuitBreaker circuitBreaker;
    private final RateLimitConfig.FailStrategy failStrategy;

    /**
     * Creates a tiered storage provider with default circuit breaker settings.
     *
     * @param l1Provider   the primary storage provider
     * @param l2Provider   the fallback storage provider
     * @param failStrategy the failure strategy (FAIL_OPEN or FAIL_CLOSED)
     */
    public TieredStorageProvider(StorageProvider l1Provider,
                                 StorageProvider l2Provider,
                                 RateLimitConfig.FailStrategy failStrategy) {
        this(l1Provider, l2Provider, failStrategy, new JitteredCircuitBreaker());
    }

    /**
     * Creates a tiered storage provider with custom circuit breaker.
     *
     * @param l1Provider     the primary storage provider
     * @param l2Provider     the fallback storage provider
     * @param failStrategy   the failure strategy
     * @param circuitBreaker the circuit breaker
     */
    public TieredStorageProvider(StorageProvider l1Provider,
                                 StorageProvider l2Provider,
                                 RateLimitConfig.FailStrategy failStrategy,
                                 JitteredCircuitBreaker circuitBreaker) {
        this.l1Provider = l1Provider;
        this.l2Provider = l2Provider;
        this.failStrategy = failStrategy;
        this.circuitBreaker = circuitBreaker;

        logger.info("TieredStorageProvider initialized: L1={}, L2={}, failStrategy={}",
                l1Provider.getClass().getSimpleName(),
                l2Provider.getClass().getSimpleName(),
                failStrategy);
    }

    @Override
    public long getCurrentTime() {
        try {
            // Try L1 first
            return circuitBreaker.execute(l1Provider::getCurrentTime);
        } catch (Exception e) {
            // Fallback to L2
            logger.trace("L1 getCurrentTime failed, using L2: {}", e.getMessage());
            return l2Provider.getCurrentTime();
        }
    }

    @Override
    public boolean tryAcquire(String key, RateLimitConfig config, long currentTime) {
        try {
            // Try L1 (primary) with circuit breaker protection
            return circuitBreaker.execute(() -> l1Provider.tryAcquire(key, config, currentTime));

        } catch (JitteredCircuitBreaker.CircuitBreakerOpenException e) {
            // Circuit is open - use L2 fallback
            return handleL1Unavailable(key, config, currentTime, "Circuit breaker OPEN");

        } catch (Exception e) {
            // L1 error - use L2 fallback
            return handleL1Unavailable(key, config, currentTime, "L1 error: " + e.getMessage());
        }
    }

    /**
     * Handles L1 unavailability by falling back to L2 or denying based on fail strategy.
     *
     * @param key         the rate limit key
     * @param config      the rate limit configuration
     * @param currentTime the current time
     * @param reason      the reason for L1 unavailability
     * @return true if allowed, false otherwise
     */
    private boolean handleL1Unavailable(String key, RateLimitConfig config,
                                        long currentTime, String reason) {
        logger.debug("L1 unavailable for key={}, reason={}, using L2 fallback", key, reason);

        // Determine strategy: use config-specific or global default
        RateLimitConfig.FailStrategy strategy = config.getFailStrategy() != null
                ? config.getFailStrategy()
                : failStrategy;

        switch (strategy) {
            case FAIL_OPEN:
                // AP mode: Prioritize availability
                // Use L2 for per-node rate limiting, but if L2 also fails, allow the request
                try {
                    boolean allowed = l2Provider.tryAcquire(key, config, currentTime);

                    if (!allowed) {
                        logger.trace("L2 denied request for key={} (AP mode)", key);
                    }

                    return allowed;
                } catch (Exception l2Exception) {
                    // Both L1 and L2 failed - FAIL_OPEN means allow the request
                    logger.warn("Both L1 and L2 failed for key={}, allowing request (FAIL_OPEN): {}",
                            key, l2Exception.getMessage());
                    return true;
                }

            case FAIL_CLOSED:
                // CP mode: Prioritize consistency
                // Deny all requests when L1 is unavailable
                logger.warn("L1 unavailable and FAIL_CLOSED strategy active, denying request for key={}", key);
                return false;

            default:
                throw new IllegalStateException("Unknown fail strategy: " + strategy);
        }
    }

    @Override
    public void reset(String key) {
        // Reset in both L1 and L2
        try {
            circuitBreaker.execute(() -> {
                l1Provider.reset(key);
                return null;
            });
        } catch (Exception e) {
            logger.debug("Failed to reset L1 for key={}: {}", key, e.getMessage());
        }

        try {
            l2Provider.reset(key);
        } catch (Exception e) {
            logger.debug("Failed to reset L2 for key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public Optional<RateLimitState> getState(String key) {
        try {
            // Try L1 first
            return circuitBreaker.execute(() -> l1Provider.getState(key));
        } catch (Exception e) {
            // Fallback to L2
            logger.trace("L1 getState failed for key={}, using L2: {}", key, e.getMessage());
            return l2Provider.getState(key);
        }
    }

    @Override
    public boolean isHealthy() {
        logger.info("L1 healthy {} | L2 healthy {}", l1Provider.isHealthy(), l2Provider.isHealthy());
        return l1Provider.isHealthy() && l2Provider.isHealthy();
    }

    @Override
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new java.util.HashMap<>();
        diagnostics.put("l1Healthy", l1Provider.isHealthy());
        diagnostics.put("l2Healthy", l2Provider.isHealthy());
        diagnostics.put("circuitState", circuitBreaker.getState().name());
        diagnostics.put("circuitFailureRate", circuitBreaker.getFailureRate());
        diagnostics.put("failStrategy", failStrategy.name());

        // Null-safe handling of provider diagnostics
        Map<String, Object> l1Diag = l1Provider.getDiagnostics();
        diagnostics.put("l1Diagnostics", l1Diag != null ? l1Diag : java.util.Collections.emptyMap());

        Map<String, Object> l2Diag = l2Provider.getDiagnostics();
        diagnostics.put("l2Diagnostics", l2Diag != null ? l2Diag : java.util.Collections.emptyMap());

        return diagnostics;
    }

    /**
     * Gets the circuit breaker state.
     *
     * @return the current circuit breaker state
     */
    public JitteredCircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }

    /**
     * Gets the circuit breaker failure rate.
     *
     * @return the failure rate (0.0 to 1.0)
     */
    public double getCircuitFailureRate() {
        return circuitBreaker.getFailureRate();
    }

    /**
     * Manually trips the circuit breaker to OPEN state.
     *
     * <p>This can be useful for maintenance or testing.
     */
    public void tripCircuit() {
        circuitBreaker.tripCircuit();
    }

    /**
     * Manually resets the circuit breaker to CLOSED state.
     */
    public void resetCircuit() {
        circuitBreaker.reset();
    }

    /**
     * Gets the L1 provider (for testing).
     *
     * @return the L1 provider
     */
    StorageProvider getL1Provider() {
        return l1Provider;
    }

    /**
     * Gets the L2 provider (for testing).
     *
     * @return the L2 provider
     */
    StorageProvider getL2Provider() {
        return l2Provider;
    }
}
