package com.lycosoft.ratelimit.resilience;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.spi.StorageProvider;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TieredStorageProvider}.
 *
 * <p>Focuses on:
 * <ul>
 *   <li>Null-safe getDiagnostics()</li>
 *   <li>L1/L2 failover behavior</li>
 *   <li>Circuit breaker integration</li>
 *   <li>Fail strategy (FAIL_OPEN vs FAIL_CLOSED)</li>
 * </ul>
 */
class TieredStorageProviderTest {

    private StorageProvider l1Provider;
    private StorageProvider l2Provider;
    private TieredStorageProvider tieredProvider;
    private RateLimitConfig config;

    @BeforeEach
    void setUp() {
        l1Provider = new InMemoryStorageProvider();
        l2Provider = new InMemoryStorageProvider();
        tieredProvider = new TieredStorageProvider(l1Provider, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        config = RateLimitConfig.builder()
            .name("test-limiter")
            .requests(100)
            .window(60)
            .build();
    }

    // ==================== Null-Safe Diagnostics Tests ====================

    @Test
    void shouldHandleNullL1Diagnostics() {
        // Given: L1 provider that returns null diagnostics
        StorageProvider nullDiagL1 = mock(StorageProvider.class);
        when(nullDiagL1.isHealthy()).thenReturn(true);
        when(nullDiagL1.getDiagnostics()).thenReturn(null);

        StorageProvider l2 = new InMemoryStorageProvider();

        TieredStorageProvider provider = new TieredStorageProvider(nullDiagL1, l2,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Get diagnostics
        Map<String, Object> diagnostics = provider.getDiagnostics();

        // Then: Should not throw, L1 diagnostics should be empty map
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.get("l1Diagnostics")).isEqualTo(Collections.emptyMap());
    }

    @Test
    void shouldHandleNullL2Diagnostics() {
        // Given: L2 provider that returns null diagnostics
        StorageProvider l1 = new InMemoryStorageProvider();

        StorageProvider nullDiagL2 = mock(StorageProvider.class);
        when(nullDiagL2.isHealthy()).thenReturn(true);
        when(nullDiagL2.getDiagnostics()).thenReturn(null);

        TieredStorageProvider provider = new TieredStorageProvider(l1, nullDiagL2,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Get diagnostics
        Map<String, Object> diagnostics = provider.getDiagnostics();

        // Then: Should not throw, L2 diagnostics should be empty map
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.get("l2Diagnostics")).isEqualTo(Collections.emptyMap());
    }

    @Test
    void shouldHandleBothNullDiagnostics() {
        // Given: Both providers return null diagnostics
        StorageProvider nullDiagL1 = mock(StorageProvider.class);
        when(nullDiagL1.isHealthy()).thenReturn(true);
        when(nullDiagL1.getDiagnostics()).thenReturn(null);

        StorageProvider nullDiagL2 = mock(StorageProvider.class);
        when(nullDiagL2.isHealthy()).thenReturn(true);
        when(nullDiagL2.getDiagnostics()).thenReturn(null);

        TieredStorageProvider provider = new TieredStorageProvider(nullDiagL1, nullDiagL2,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Get diagnostics
        Map<String, Object> diagnostics = provider.getDiagnostics();

        // Then: Should not throw
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.get("l1Diagnostics")).isEqualTo(Collections.emptyMap());
        assertThat(diagnostics.get("l2Diagnostics")).isEqualTo(Collections.emptyMap());
    }

    @Test
    void shouldIncludeCircuitBreakerStateInDiagnostics() {
        // When: Get diagnostics
        Map<String, Object> diagnostics = tieredProvider.getDiagnostics();

        // Then: Should include circuit breaker info
        assertThat(diagnostics).containsKey("circuitState");
        assertThat(diagnostics).containsKey("circuitFailureRate");
        assertThat(diagnostics.get("circuitState")).isEqualTo("CLOSED");
    }

    @Test
    void shouldIncludeHealthStatusInDiagnostics() {
        // When: Get diagnostics
        Map<String, Object> diagnostics = tieredProvider.getDiagnostics();

        // Then: Should include health status
        assertThat(diagnostics).containsKey("l1Healthy");
        assertThat(diagnostics).containsKey("l2Healthy");
        assertThat(diagnostics.get("l1Healthy")).isEqualTo(true);
        assertThat(diagnostics.get("l2Healthy")).isEqualTo(true);
    }

    @Test
    void shouldIncludeProviderDiagnosticsWhenNotNull() {
        // Given: Providers with real diagnostics
        Map<String, Object> l1Diag = new HashMap<>();
        l1Diag.put("type", "Redis");
        l1Diag.put("connections", 10);

        StorageProvider l1Mock = mock(StorageProvider.class);
        when(l1Mock.isHealthy()).thenReturn(true);
        when(l1Mock.getDiagnostics()).thenReturn(l1Diag);

        TieredStorageProvider provider = new TieredStorageProvider(l1Mock, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Get diagnostics
        Map<String, Object> diagnostics = provider.getDiagnostics();

        // Then: Should include L1 diagnostics
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedL1Diag = (Map<String, Object>) diagnostics.get("l1Diagnostics");
        assertThat(retrievedL1Diag).containsEntry("type", "Redis");
        assertThat(retrievedL1Diag).containsEntry("connections", 10);
    }

    // ==================== L1/L2 Failover Tests ====================

    @Test
    void shouldUseL1WhenHealthy() {
        // When: Try to acquire
        boolean allowed = tieredProvider.tryAcquire("key1", config, System.currentTimeMillis());

        // Then: Should be allowed (L1 used)
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldFallbackToL2WhenL1Fails() {
        // Given: Failing L1 provider
        StorageProvider failingL1 = mock(StorageProvider.class);
        when(failingL1.tryAcquire(anyString(), any(), anyLong()))
            .thenThrow(new RuntimeException("L1 connection failed"));
        when(failingL1.isHealthy()).thenReturn(false);

        TieredStorageProvider provider = new TieredStorageProvider(failingL1, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Try to acquire
        boolean allowed = provider.tryAcquire("key1", config, System.currentTimeMillis());

        // Then: Should fall back to L2 and be allowed
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldDenyWhenL1FailsAndFailClosed() {
        // Given: Failing L1 with FAIL_CLOSED strategy
        StorageProvider failingL1 = mock(StorageProvider.class);
        when(failingL1.tryAcquire(anyString(), any(), anyLong()))
            .thenThrow(new RuntimeException("L1 connection failed"));
        when(failingL1.isHealthy()).thenReturn(false);

        TieredStorageProvider provider = new TieredStorageProvider(failingL1, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_CLOSED);

        // When: Try to acquire
        boolean allowed = provider.tryAcquire("key1", config, System.currentTimeMillis());

        // Then: Should deny request
        assertThat(allowed).isFalse();
    }

    // ==================== Circuit Breaker Tests ====================

    @Test
    void shouldReportCircuitState() {
        assertThat(tieredProvider.getCircuitState()).isEqualTo(JitteredCircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldReportCircuitFailureRate() {
        assertThat(tieredProvider.getCircuitFailureRate()).isEqualTo(0.0);
    }

    @Test
    void shouldAllowManualCircuitTrip() {
        // When: Trip circuit
        tieredProvider.tripCircuit();

        // Then: Circuit should be open
        assertThat(tieredProvider.getCircuitState()).isEqualTo(JitteredCircuitBreaker.State.OPEN);
    }

    @Test
    void shouldAllowManualCircuitReset() {
        // Given: Tripped circuit
        tieredProvider.tripCircuit();

        // When: Reset circuit
        tieredProvider.resetCircuit();

        // Then: Circuit should be closed
        assertThat(tieredProvider.getCircuitState()).isEqualTo(JitteredCircuitBreaker.State.CLOSED);
    }

    // ==================== Health Check Tests ====================

    @Test
    void shouldBeHealthyWhenBothProvidersHealthy() {
        assertThat(tieredProvider.isHealthy()).isTrue();
    }

    @Test
    void shouldBeUnhealthyWhenL1Unhealthy() {
        StorageProvider unhealthyL1 = mock(StorageProvider.class);
        when(unhealthyL1.isHealthy()).thenReturn(false);

        TieredStorageProvider provider = new TieredStorageProvider(unhealthyL1, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        assertThat(provider.isHealthy()).isFalse();
    }

    @Test
    void shouldBeUnhealthyWhenL2Unhealthy() {
        StorageProvider unhealthyL2 = mock(StorageProvider.class);
        when(unhealthyL2.isHealthy()).thenReturn(false);

        TieredStorageProvider provider = new TieredStorageProvider(l1Provider, unhealthyL2,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        assertThat(provider.isHealthy()).isFalse();
    }

    // ==================== Reset Tests ====================

    @Test
    void shouldResetBothProviders() {
        // Given: State in both providers
        tieredProvider.tryAcquire("key1", config, System.currentTimeMillis());

        // When: Reset
        tieredProvider.reset("key1");

        // Then: Both should be reset (no exception)
        // Verify by checking state is empty
        Optional<RateLimitState> l1State = l1Provider.getState("key1");
        Optional<RateLimitState> l2State = l2Provider.getState("key1");

        // After reset, state should be empty
        assertThat(l1State).isEmpty();
        assertThat(l2State).isEmpty();
    }

    // ==================== GetState Tests ====================

    @Test
    void shouldGetStateFromL1WhenAvailable() {
        // Given: State in L1
        tieredProvider.tryAcquire("key1", config, System.currentTimeMillis());

        // When: Get state
        Optional<RateLimitState> state = tieredProvider.getState("key1");

        // Then: Should have state
        assertThat(state).isPresent();
    }

    @Test
    void shouldFallbackToL2ForStateWhenL1Fails() {
        // Given: Failing L1
        StorageProvider failingL1 = mock(StorageProvider.class);
        when(failingL1.getState(anyString()))
            .thenThrow(new RuntimeException("L1 failed"));

        // Put state in L2
        l2Provider.tryAcquire("key1", config, System.currentTimeMillis());

        TieredStorageProvider provider = new TieredStorageProvider(failingL1, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Get state (L1 will fail, fall back to L2)
        Optional<RateLimitState> state = provider.getState("key1");

        // Then: Should get state from L2
        assertThat(state).isPresent();
    }

    // ==================== GetCurrentTime Tests ====================

    @Test
    void shouldGetTimeFromL1WhenAvailable() {
        long time = tieredProvider.getCurrentTime();
        assertThat(time).isPositive();
    }

    @Test
    void shouldFallbackToL2ForTimeWhenL1Fails() {
        // Given: Failing L1
        StorageProvider failingL1 = mock(StorageProvider.class);
        when(failingL1.getCurrentTime())
            .thenThrow(new RuntimeException("L1 failed"));

        TieredStorageProvider provider = new TieredStorageProvider(failingL1, l2Provider,
            RateLimitConfig.FailStrategy.FAIL_OPEN);

        // When: Get time
        long time = provider.getCurrentTime();

        // Then: Should get time from L2
        assertThat(time).isPositive();
    }
}
