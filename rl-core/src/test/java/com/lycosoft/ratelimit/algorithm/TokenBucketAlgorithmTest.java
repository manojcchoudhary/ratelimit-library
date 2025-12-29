package com.lycosoft.ratelimit.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TokenBucketAlgorithm with virtual time manipulation.
 */
class TokenBucketAlgorithmTest {
    
    @Test
    void shouldStartWithFullBucket() {
        // Given: Token bucket with capacity=10, refill rate=5/sec
        double capacity = 10.0;
        double refillRatePerMs = 5.0 / 1000.0; // 5 tokens per second = 0.005 per ms
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);
        
        long currentTime = 1000L;
        
        // When: First request consuming all 10 tokens
        TokenBucketAlgorithm.BucketState result = algorithm.tryConsume(null, 10, currentTime);
        
        // Then: Request allowed, bucket now empty
        assertTrue(result.allowed());
        assertThat(result.tokens()).isEqualTo(0.0);
    }
    
    @Test
    void shouldDenyWhenInsufficientTokens() {
        // Given: Token bucket with capacity=10
        double capacity = 10.0;
        double refillRatePerMs = 5.0 / 1000.0;
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);
        
        long time1 = 1000L;
        
        // When: First request consumes all 10 tokens
        TokenBucketAlgorithm.BucketState state1 = algorithm.tryConsume(null, 10, time1);
        assertTrue(state1.allowed());
        
        // When: Immediate second request (no time passed, no refill)
        TokenBucketAlgorithm.BucketState state2 = algorithm.tryConsume(state1, 1, time1);
        
        // Then: Second request denied
        assertFalse(state2.allowed());
        assertThat(state2.tokens()).isEqualTo(0.0);
    }
    
    @Test
    void shouldRefillTokensOverTime() {
        // Given: Token bucket with capacity=10, refill=5 tokens/sec
        double capacity = 10.0;
        double refillRatePerMs = 5.0 / 1000.0; // 0.005 tokens/ms
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);
        
        long time1 = 1000L;
        long time2 = 2000L; // +1 second = +5 tokens
        
        // When: Consume all 10 tokens
        TokenBucketAlgorithm.BucketState state1 = algorithm.tryConsume(null, 10, time1);
        assertTrue(state1.allowed());
        assertThat(state1.tokens()).isEqualTo(0.0);
        
        // When: Wait 1 second (should refill 5 tokens)
        TokenBucketAlgorithm.BucketState state2 = algorithm.tryConsume(state1, 5, time2);
        
        // Then: Request allowed with exactly 5 refilled tokens
        assertTrue(state2.allowed());
        assertThat(state2.tokens()).isEqualTo(0.0); // Consumed all refilled tokens
    }
    
    @Test
    void shouldCapAtMaximumCapacity() {
        // Given: Token bucket with capacity=10
        double capacity = 10.0;
        double refillRatePerMs = 5.0 / 1000.0;
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);
        
        long time1 = 1000L;
        long time2 = 5000L; // +4 seconds = +20 tokens (but capped at 10)
        
        // When: Consume 5 tokens (leaving 5)
        TokenBucketAlgorithm.BucketState state1 = algorithm.tryConsume(null, 5, time1);
        assertTrue(state1.allowed());
        assertThat(state1.tokens()).isEqualTo(5.0);
        
        // When: Wait 4 seconds (would add 20, but capped at capacity)
        TokenBucketAlgorithm.BucketState state2 = algorithm.tryConsume(state1, 10, time2);
        
        // Then: Only 10 tokens available (5 remaining + 5 refilled, capped at 10)
        assertTrue(state2.allowed());
        assertThat(state2.tokens()).isEqualTo(0.0);
    }
    
    @Test
    void shouldHandleBurstThenSustainedLoad() {
        // Given: Token bucket with capacity=10, refill=5/sec
        double capacity = 10.0;
        double refillRatePerMs = 5.0 / 1000.0;
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);
        
        VirtualClock clock = new VirtualClock(1000L);
        
        // When: Initial burst (consume all 10 tokens)
        TokenBucketAlgorithm.BucketState state = algorithm.tryConsume(null, 10, clock.currentTime());
        assertTrue(state.allowed());
        
        // When: Immediate second request (no tokens available)
        state = algorithm.tryConsume(state, 1, clock.currentTime());
        assertFalse(state.allowed());
        
        // When: Sustained load at 1 req/200ms (within 5 req/sec capacity)
        for (int i = 0; i < 100; i++) {
            clock.advance(200); // Advance 200ms (refills 1 token)
            state = algorithm.tryConsume(state, 1, clock.currentTime());
            assertTrue(state.allowed(), "Request " + i + " should be allowed");
        }
    }
    
    @Test
    void shouldRejectExcessiveTokenRequests() {
        // Given: Token bucket with capacity=10
        double capacity = 10.0;
        double refillRatePerMs = 5.0 / 1000.0;
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(capacity, refillRatePerMs);
        
        long currentTime = 1000L;
        
        // When: Request more tokens than capacity
        TokenBucketAlgorithm.BucketState result = algorithm.tryConsume(null, 20, currentTime);
        
        // Then: Request denied (can never have 20 tokens)
        assertFalse(result.allowed());
    }
    
    @Test
    void shouldValidateConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () -> 
            new TokenBucketAlgorithm(0, 1.0));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new TokenBucketAlgorithm(-5, 1.0));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new TokenBucketAlgorithm(10, 0));
        
        assertThrows(IllegalArgumentException.class, () -> 
            new TokenBucketAlgorithm(10, -1.0));
    }
    
    @Test
    void shouldValidateTryConsumeParameters() {
        TokenBucketAlgorithm algorithm = new TokenBucketAlgorithm(10, 0.005);
        
        assertThrows(IllegalArgumentException.class, () -> 
            algorithm.tryConsume(null, 0, 1000L));
        
        assertThrows(IllegalArgumentException.class, () -> 
            algorithm.tryConsume(null, -5, 1000L));
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
    }
}
