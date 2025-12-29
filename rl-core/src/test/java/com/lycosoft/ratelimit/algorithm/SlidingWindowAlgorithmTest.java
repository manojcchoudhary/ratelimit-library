package com.lycosoft.ratelimit.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SlidingWindowAlgorithm with virtual time manipulation.
 */
class SlidingWindowAlgorithmTest {
    
    @Test
    void shouldAllowRequestsWithinLimit() {
        // Given: Sliding window with limit=5, window=10 seconds
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(5, 10_000);
        
        VirtualClock clock = new VirtualClock(1000L);
        
        // When: Making 5 requests within the window
        SlidingWindowAlgorithm.WindowState state = null;
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed(), "Request " + i + " should be allowed");
        }
    }
    
    @Test
    void shouldDenyRequestWhenLimitExceeded() {
        // Given: Sliding window with limit=3, window=10 seconds
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(3, 10_000);
        
        VirtualClock clock = new VirtualClock(1000L);
        
        // When: Making 3 requests (fill the window)
        SlidingWindowAlgorithm.WindowState state = null;
        for (int i = 0; i < 3; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed());
        }
        
        // When: Making 4th request
        state = algorithm.tryConsume(state, clock.currentTime());
        
        // Then: Request denied
        assertFalse(state.isAllowed());
    }
    
    @Test
    void shouldRotateWindowsCorrectly() {
        // Given: Sliding window with limit=5, window=10 seconds
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(5, 10_000);
        
        VirtualClock clock = new VirtualClock(0L);
        
        // When: Making 5 requests in window 0-10s
        SlidingWindowAlgorithm.WindowState state = null;
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed());
            clock.advance(1000); // +1 second
        }
        
        // When: Advancing to next window (10-20s)
        clock.setTime(15_000L); // 15 seconds (middle of next window)
        
        // When: Making a request in the new window
        state = algorithm.tryConsume(state, clock.currentTime());
        
        // Then: Previous window should exist after consuming in new window
        assertNotNull(state.getPreviousWindow());
        assertThat(state.getPreviousWindow().getCount()).isGreaterThan(0);
        
        // And: Request should be allowed (weighted average)
        assertTrue(state.isAllowed());
    }
    
    @Test
    void shouldUseWeightedMovingAverage() {
        // Given: Sliding window with limit=10, window=10 seconds
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(10, 10_000);
        
        VirtualClock clock = new VirtualClock(0L);
        
        // When: Making 10 requests at t=0 (fill previous window)
        SlidingWindowAlgorithm.WindowState state = null;
        for (int i = 0; i < 10; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed());
        }
        
        // When: Advancing halfway through next window (t=15s)
        clock.setTime(15_000L);
        
        // At t=15s:
        // - We're 5s into the current window (10-20s)
        // - Overlap weight = (10000 - 5000) / 10000 = 0.5
        // - Estimated count = 10 * 0.5 + 0 = 5
        // - Should allow 5 more requests before hitting limit
        
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed(), "Request " + i + " should be allowed");
        }
        
        // Next request should be denied (estimated count = 10)
        state = algorithm.tryConsume(state, clock.currentTime());
        assertFalse(state.isAllowed());
    }
    
    @Test
    void shouldResetAfterWindowPasses() {
        // Given: Sliding window with limit=5, window=10 seconds
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(5, 10_000);
        
        VirtualClock clock = new VirtualClock(0L);
        
        // When: Making 5 requests at t=0
        SlidingWindowAlgorithm.WindowState state = null;
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed());
        }
        
        // When: Advancing past the window (t=25s, two windows later)
        clock.setTime(25_000L);
        
        // Then: Should allow new requests (old window doesn't affect count)
        for (int i = 0; i < 5; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed(), "Request " + i + " should be allowed after reset");
        }
    }
    
    @Test
    void shouldHandleMinimumGranularity() {
        // Window size must be at least 1 second
        assertThrows(IllegalArgumentException.class, () -> 
            new SlidingWindowAlgorithm(10, 999));
        
        // Exactly 1 second should work
        assertDoesNotThrow(() -> 
            new SlidingWindowAlgorithm(10, 1000));
    }
    
    @Test
    void shouldValidateConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () -> 
            new SlidingWindowAlgorithm(0, 10_000));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new SlidingWindowAlgorithm(-5, 10_000));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new SlidingWindowAlgorithm(10, 500));
    }
    
    @Test
    void shouldHandleSustainedLoad() {
        // Given: Sliding window with limit=100, window=60 seconds
        SlidingWindowAlgorithm algorithm = new SlidingWindowAlgorithm(100, 60_000);
        
        VirtualClock clock = new VirtualClock(0L);
        
        // When: Sustained load at exactly the limit (1 request per 600ms = 100 req/60s)
        SlidingWindowAlgorithm.WindowState state = null;
        
        // First 100 requests should be allowed (within limit)
        for (int i = 0; i < 100; i++) {
            state = algorithm.tryConsume(state, clock.currentTime());
            assertTrue(state.isAllowed(), "Request " + i + " should be allowed");
            clock.advance(600); // Advance 600ms
        }
        
        // After 100 requests in 60 seconds, we're at the limit
        // The next request should be denied until some requests expire from the window
        state = algorithm.tryConsume(state, clock.currentTime());
        assertFalse(state.isAllowed(), "Request 101 should be denied (limit reached)");
        
        // Advance time to let some requests expire from the window
        clock.advance(10_000); // Advance 10 seconds
        
        // Now new requests should be allowed again
        state = algorithm.tryConsume(state, clock.currentTime());
        assertTrue(state.isAllowed(), "Request should be allowed after window shift");
    }
    
    /**
     * Virtual clock for testing time-dependent logic.
     */
    static class VirtualClock {
        private long currentTime;
        
        VirtualClock(long initialTime) {
            this.currentTime = initialTime;
        }
        
        long currentTime() {
            return currentTime;
        }
        
        void advance(long millis) {
            this.currentTime += millis;
        }
        
        void setTime(long time) {
            this.currentTime = time;
        }
    }
}
