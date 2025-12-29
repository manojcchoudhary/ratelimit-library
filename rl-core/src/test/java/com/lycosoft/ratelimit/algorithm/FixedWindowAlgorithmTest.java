package com.lycosoft.ratelimit.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lycosoft.ratelimit.algorithm.FixedWindowAlgorithm.WindowState;

/**
 * Unit tests for FixedWindowAlgorithm.
 */
class FixedWindowAlgorithmTest {
    
    private FixedWindowAlgorithm algorithm;
    private static final int WINDOW_SECONDS = 60;
    private static final int LIMIT = 10;
    
    @BeforeEach
    void setUp() {
        algorithm = new FixedWindowAlgorithm(WINDOW_SECONDS);
    }
    
    @Test
    void testConstructor_ValidWindowSeconds() {
        FixedWindowAlgorithm algo = new FixedWindowAlgorithm(60);
        assertThat(algo).isNotNull();
    }
    
    @Test
    void testConstructor_InvalidWindowSeconds() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FixedWindowAlgorithm(0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new FixedWindowAlgorithm(-1);
        });
    }
    
    @Test
    void testTryAcquire_FirstRequest_ShouldAllow() {
        long currentTime = System.currentTimeMillis();
        
        WindowState result = algorithm.tryAcquire(null, LIMIT, currentTime);
        
        assertThat(result).isNotNull();
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRequestCount()).isEqualTo(1);
    }
    
    @Test
    void testTryAcquire_WithinLimit_ShouldAllow() {
        long currentTime = System.currentTimeMillis();
        WindowState state = null;
        
        // Make 5 requests (all should be allowed)
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
            assertThat(state.isAllowed()).isTrue();
            assertThat(state.getRequestCount()).isEqualTo(i + 1);
        }
    }
    
    @Test
    void testTryAcquire_ExceedLimit_ShouldDeny() {
        long currentTime = System.currentTimeMillis();
        WindowState state = null;
        
        // Make LIMIT requests (all allowed)
        for (int i = 0; i < LIMIT; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
            assertThat(state.isAllowed()).isTrue();
        }
        
        // Next request should be denied
        WindowState deniedState = algorithm.tryAcquire(state, LIMIT, currentTime);
        assertThat(deniedState.isAllowed()).isFalse();
        assertThat(deniedState.getRequestCount()).isEqualTo(LIMIT);
    }
    
    @Test
    void testTryAcquire_NewWindow_ShouldResetCounter() {
        long currentTime = System.currentTimeMillis();
        
        // Fill up current window
        WindowState state = null;
        for (int i = 0; i < LIMIT; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
        }
        
        assertThat(state.getRequestCount()).isEqualTo(LIMIT);
        
        // Move to next window (61 seconds later)
        long nextWindowTime = currentTime + (WINDOW_SECONDS + 1) * 1000L;
        
        WindowState newWindowState = algorithm.tryAcquire(state, LIMIT, nextWindowTime);
        
        assertThat(newWindowState.isAllowed()).isTrue();
        assertThat(newWindowState.getRequestCount()).isEqualTo(1);
        assertThat(newWindowState.getWindowNumber()).isNotEqualTo(state.getWindowNumber());
    }
    
    @Test
    void testGetRemaining_EmptyWindow_ShouldReturnFullLimit() {
        long currentTime = System.currentTimeMillis();
        
        int remaining = algorithm.getRemaining(null, LIMIT, currentTime);
        
        assertThat(remaining).isEqualTo(LIMIT);
    }
    
    @Test
    void testGetRemaining_PartiallyUsed_ShouldReturnCorrectValue() {
        long currentTime = System.currentTimeMillis();
        
        // Use 3 requests
        WindowState state = null;
        for (int i = 0; i < 3; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
        }
        
        int remaining = algorithm.getRemaining(state, LIMIT, currentTime);
        
        assertThat(remaining).isEqualTo(LIMIT - 3);
    }
    
    @Test
    void testGetRemaining_LimitExceeded_ShouldReturnZero() {
        long currentTime = System.currentTimeMillis();
        
        // Exceed limit
        WindowState state = null;
        for (int i = 0; i < LIMIT + 5; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
        }
        
        int remaining = algorithm.getRemaining(state, LIMIT, currentTime);
        
        assertThat(remaining).isEqualTo(0);
    }
    
    @Test
    void testGetRemaining_NewWindow_ShouldReturnFullLimit() {
        long currentTime = System.currentTimeMillis();
        
        // Use requests in current window
        WindowState state = null;
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
        }
        
        // Move to next window
        long nextWindowTime = currentTime + (WINDOW_SECONDS + 1) * 1000L;
        
        int remaining = algorithm.getRemaining(state, LIMIT, nextWindowTime);
        
        assertThat(remaining).isEqualTo(LIMIT);
    }
    
    @Test
    void testGetResetTime_ShouldReturnCorrectValue() {
        long currentTime = System.currentTimeMillis();
        
        long resetTime = algorithm.getResetTime(currentTime);
        
        // Reset time should be less than window size and greater than 0
        assertThat(resetTime).isGreaterThan(0);
        assertThat(resetTime).isLessThanOrEqualTo(WINDOW_SECONDS);
    }
    
    @Test
    void testWindowState_Getters() {
        WindowState state = new WindowState(5L, 7, true);
        
        assertThat(state.getWindowNumber()).isEqualTo(5L);
        assertThat(state.getRequestCount()).isEqualTo(7);
        assertThat(state.isAllowed()).isTrue();
    }
    
    @Test
    void testWindowState_DefaultConstructor() {
        WindowState state = new WindowState(10L, 3);
        
        assertThat(state.getWindowNumber()).isEqualTo(10L);
        assertThat(state.getRequestCount()).isEqualTo(3);
        assertThat(state.isAllowed()).isTrue(); // Default is allowed
    }
    
    @Test
    void testBurstAtWindowBoundary() {
        long currentTime = System.currentTimeMillis();
        
        // Fill up current window completely
        WindowState state = null;
        for (int i = 0; i < LIMIT; i++) {
            state = algorithm.tryAcquire(state, LIMIT, currentTime);
        }
        
        assertThat(state.getRequestCount()).isEqualTo(LIMIT);
        assertThat(state.isAllowed()).isTrue();
        
        // Try one more in same window - should be denied
        WindowState deniedState = algorithm.tryAcquire(state, LIMIT, currentTime);
        assertThat(deniedState.isAllowed()).isFalse();
        
        // Move to next window by 1 millisecond after window boundary
        long nextWindowTime = currentTime + (WINDOW_SECONDS * 1000L) + 1;
        
        // Should be allowed again with fresh window
        WindowState nextWindowState = algorithm.tryAcquire(state, LIMIT, nextWindowTime);
        assertThat(nextWindowState.isAllowed()).isTrue();
        assertThat(nextWindowState.getRequestCount()).isEqualTo(1);
    }
}
