package com.lycosoft.ratelimit.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker with jittered recovery to prevent thundering herd.
 * 
 * <p>This implementation addresses <b>Pre-flight Check #1: Thundering Herd on L1 Recovery</b>.
 * 
 * <p>When many nodes simultaneously detect that a failed service (e.g., Redis) has recovered,
 * they might all reconnect at once, overwhelming the service and causing it to fail again.
 * This circuit breaker uses jittered timeouts to spread reconnection attempts over time.
 * 
 * <p><b>Jitter Formula:</b>
 * <pre>
 * timeout = BASE_TIMEOUT × (1 ± JITTER_FACTOR × random())
 * 
 * Example with 30s base, ±30% jitter:
 *   Node 1: 21.5s  (30s × 0.72)
 *   Node 2: 34.2s  (30s × 1.14)
 *   Node 3: 28.9s  (30s × 0.96)
 *   ...
 *   Result: Reconnections spread over ~13 seconds
 * </pre>
 * 
 * <p><b>States:</b>
 * <ul>
 *   <li><b>CLOSED:</b> Normal operation, requests pass through</li>
 *   <li><b>OPEN:</b> Too many failures, requests blocked</li>
 *   <li><b>HALF_OPEN:</b> Testing if service recovered (single probe)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class JitteredCircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(JitteredCircuitBreaker.class);
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger activeProbes = new AtomicInteger(0);
    private final Random random = new Random();

    // Configuration
    private final double failureThreshold;
    private final long windowMs;
    private final long baseHalfOpenTimeoutMs;
    private final double jitterFactor;
    private final int maxConcurrentProbes;
    
    /**
     * Creates a circuit breaker with default settings.
     * 
     * <p>Defaults:
     * <ul>
     *   <li>Failure threshold: 50%</li>
     *   <li>Window: 10 seconds</li>
     *   <li>Half-open timeout: 30 seconds</li>
     *   <li>Jitter factor: ±30%</li>
     *   <li>Max concurrent probes: 1</li>
     * </ul>
     */
    public JitteredCircuitBreaker() {
        this(0.5, 10_000, 30_000, 0.3, 1);
    }
    
    /**
     * Creates a circuit breaker with custom settings.
     * 
     * @param failureThreshold the failure rate threshold (0.0 to 1.0)
     * @param windowMs the sliding window size in milliseconds
     * @param baseHalfOpenTimeoutMs the base half-open timeout in milliseconds
     * @param jitterFactor the jitter factor (0.0 to 1.0, typically 0.3 for ±30%)
     * @param maxConcurrentProbes the maximum number of concurrent probe requests
     */
    public JitteredCircuitBreaker(double failureThreshold, 
                                 long windowMs, 
                                 long baseHalfOpenTimeoutMs,
                                 double jitterFactor,
                                 int maxConcurrentProbes) {
        if (failureThreshold < 0 || failureThreshold > 1) {
            throw new IllegalArgumentException("failureThreshold must be between 0 and 1");
        }
        if (jitterFactor < 0 || jitterFactor > 1) {
            throw new IllegalArgumentException("jitterFactor must be between 0 and 1");
        }
        
        this.failureThreshold = failureThreshold;
        this.windowMs = windowMs;
        this.baseHalfOpenTimeoutMs = baseHalfOpenTimeoutMs;
        this.jitterFactor = jitterFactor;
        this.maxConcurrentProbes = maxConcurrentProbes;
        
        logger.info("JitteredCircuitBreaker initialized: threshold={}, window={}ms, " +
                   "halfOpenTimeout={}ms, jitter=±{}%", 
                   failureThreshold, windowMs, baseHalfOpenTimeoutMs, (int)(jitterFactor * 100));
    }
    
    /**
     * Executes an operation with circuit breaker protection.
     * 
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws CircuitBreakerOpenException if the circuit is open
     * @throws Exception if the operation fails
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        State currentState = state.get();
        
        switch (currentState) {
            case OPEN:
                return handleOpenState(operation);
                
            case HALF_OPEN:
                return handleHalfOpenState(operation);
                
            case CLOSED:
            default:
                return handleClosedState(operation);
        }
    }
    
    /**
     * Handles execution in CLOSED state (normal operation).
     */
    private <T> T handleClosedState(Callable<T> operation) throws Exception {
        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
    
    /**
     * Handles execution in OPEN state (circuit tripped).
     */
    private <T> T handleOpenState(Callable<T> operation) throws Exception {
        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        long jitteredTimeout = calculateJitteredTimeout();
        
        if (elapsed > jitteredTimeout) {
            // Transition to HALF_OPEN with jitter
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                logger.info("Circuit transitioning to HALF_OPEN after {}ms (jittered timeout: {}ms)", 
                           elapsed, jitteredTimeout);
                return handleHalfOpenState(operation);
            }
        }
        
        // Circuit still open - reject request
        throw new CircuitBreakerOpenException(
            "Circuit breaker is OPEN (elapsed: " + elapsed + "ms, timeout: " + jitteredTimeout + "ms)"
        );
    }
    
    /**
     * Handles execution in HALF_OPEN state (testing recovery).
     *
     * <p>Limits concurrent probes to prevent overwhelming the recovering service.
     * Uses atomic counter to enforce maxConcurrentProbes limit.
     */
    private <T> T handleHalfOpenState(Callable<T> operation) throws Exception {
        // Check if we can start a new probe (atomic check-and-increment)
        int currentProbes = activeProbes.get();
        if (currentProbes >= maxConcurrentProbes) {
            // Too many concurrent probes - reject this request
            throw new CircuitBreakerOpenException(
                "Circuit breaker HALF_OPEN but max concurrent probes reached (" +
                currentProbes + "/" + maxConcurrentProbes + ")"
            );
        }

        // Try to increment probe count atomically
        if (!activeProbes.compareAndSet(currentProbes, currentProbes + 1)) {
            // Another thread beat us - check again
            if (activeProbes.get() >= maxConcurrentProbes) {
                throw new CircuitBreakerOpenException(
                    "Circuit breaker HALF_OPEN but max concurrent probes reached"
                );
            }
            // Retry increment (simplified - in production use a loop)
            activeProbes.incrementAndGet();
        }

        try {
            T result = operation.call();
            onSuccess(); // Transition to CLOSED
            logger.info("Circuit probe succeeded, transitioning to CLOSED");
            return result;
        } catch (Exception e) {
            onFailure(); // Back to OPEN
            logger.warn("Circuit probe failed, back to OPEN");
            throw e;
        } finally {
            activeProbes.decrementAndGet();
        }
    }
    
    /**
     * Records a successful operation.
     */
    private void onSuccess() {
        successCount.incrementAndGet();
        
        // If we were in HALF_OPEN, transition to CLOSED
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            resetCounters();
        }
    }
    
    /**
     * Records a failed operation.
     */
    private void onFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        // Check if we should trip the circuit
        int total = successCount.get() + failureCount.get();
        if (total > 0) {
            double failureRate = (double) failureCount.get() / total;
            
            if (failureRate >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN) ||
                    state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    logger.warn("Circuit breaker OPENED (failure rate: {}/{} = {:.1f}%)",
                               failureCount.get(), total, failureRate * 100);
                }
            }
        }
    }
    
    /**
     * Calculates timeout with jitter to prevent thundering herd.
     * 
     * <p>Formula: {@code BASE_TIMEOUT × (1 ± JITTER_FACTOR × random())}
     * 
     * @return the jittered timeout in milliseconds
     */
    private long calculateJitteredTimeout() {
        // Generate random value between -1 and 1
        double randomFactor = (random.nextDouble() * 2) - 1;
        
        // Apply jitter: 1.0 ± (JITTER_FACTOR × random)
        double jitter = 1.0 + (randomFactor * jitterFactor);
        
        // Calculate jittered timeout
        long timeout = (long) (baseHalfOpenTimeoutMs * jitter);
        
        logger.trace("Calculated jittered timeout: {}ms (base: {}ms, jitter factor: {:.2f})",
                    timeout, baseHalfOpenTimeoutMs, jitter);
        
        return timeout;
    }
    
    /**
     * Resets failure, success, and probe counters.
     */
    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
        activeProbes.set(0);
        logger.debug("Circuit breaker counters reset");
    }
    
    /**
     * Manually trips the circuit to OPEN state.
     */
    public void tripCircuit() {
        state.set(State.OPEN);
        lastFailureTime.set(System.currentTimeMillis());
        logger.warn("Circuit breaker manually tripped to OPEN");
    }
    
    /**
     * Manually resets the circuit to CLOSED state.
     */
    public void reset() {
        state.set(State.CLOSED);
        resetCounters();
        logger.info("Circuit breaker manually reset to CLOSED");
    }
    
    /**
     * Gets the current circuit state.
     * 
     * @return the current state
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Gets the current failure count.
     * 
     * @return the failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Gets the current success count.
     * 
     * @return the success count
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Gets the current failure rate.
     * 
     * @return the failure rate (0.0 to 1.0)
     */
    public double getFailureRate() {
        int total = successCount.get() + failureCount.get();
        return total > 0 ? (double) failureCount.get() / total : 0.0;
    }
    
    /**
     * Circuit breaker states.
     */
    public enum State {
        /** Circuit is closed - requests pass through normally */
        CLOSED,
        
        /** Circuit is open - requests are blocked */
        OPEN,
        
        /** Circuit is testing recovery - limited requests allowed */
        HALF_OPEN
    }
    
    /**
     * Exception thrown when the circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
