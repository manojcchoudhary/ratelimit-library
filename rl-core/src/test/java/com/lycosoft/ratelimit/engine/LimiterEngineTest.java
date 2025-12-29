package com.lycosoft.ratelimit.engine;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import com.lycosoft.ratelimit.spi.KeyResolver;
import com.lycosoft.ratelimit.storage.InMemoryStorageProvider;
import com.lycosoft.ratelimit.storage.StaticKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link LimiterEngine}.
 */
class LimiterEngineTest {
    
    private InMemoryStorageProvider storageProvider;
    private StaticKeyResolver keyResolver;
    private LimiterEngine engine;
    
    @BeforeEach
    void setUp() {
        storageProvider = new InMemoryStorageProvider();
        keyResolver = new StaticKeyResolver("test-key");
        engine = new LimiterEngine(storageProvider, keyResolver, null, null);
    }
    
    @Test
    void shouldAllowRequestsWithinLimit() {
        // Given: Token bucket with 10 requests capacity
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test-limiter")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(10)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("test-key")
            .build();
        
        // When: Making 10 requests
        for (int i = 0; i < 10; i++) {
            RateLimitDecision decision = engine.tryAcquire(context, config);
            
            // Then: All requests allowed
            assertTrue(decision.isAllowed(), "Request " + i + " should be allowed");
            assertThat(decision.getLimiterName()).isEqualTo("test-limiter");
            assertThat(decision.getLimit()).isEqualTo(10);
        }
    }
    
    @Test
    void shouldDenyRequestWhenLimitExceeded() {
        // Given: Token bucket with 5 requests capacity
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test-limiter")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(5)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("test-key")
            .build();
        
        // When: Making 5 requests (consume all tokens)
        for (int i = 0; i < 5; i++) {
            RateLimitDecision decision = engine.tryAcquire(context, config);
            assertTrue(decision.isAllowed());
        }
        
        // When: Making 6th request
        RateLimitDecision decision = engine.tryAcquire(context, config);
        
        // Then: Request denied
        assertFalse(decision.isAllowed());
        assertThat(decision.getLimiterName()).isEqualTo("test-limiter");
        assertThat(decision.getRemaining()).isEqualTo(0);
        assertThat(decision.getReason()).contains("Rate limit exceeded");
    }
    
    @Test
    void shouldUseSlidingWindowAlgorithm() {
        // Given: Sliding window with 5 requests per second
        RateLimitConfig config = RateLimitConfig.builder()
            .name("sliding-limiter")
            .algorithm(RateLimitConfig.Algorithm.SLIDING_WINDOW)
            .requests(5)
            .window(1)
            .windowUnit(TimeUnit.SECONDS)
            .build();
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("test-key")
            .build();
        
        // When: Making 5 requests
        for (int i = 0; i < 5; i++) {
            RateLimitDecision decision = engine.tryAcquire(context, config);
            assertTrue(decision.isAllowed(), "Request " + i + " should be allowed");
        }
        
        // When: Making 6th request
        RateLimitDecision decision = engine.tryAcquire(context, config);
        
        // Then: Request denied
        assertFalse(decision.isAllowed());
    }
    
    @Test
    void shouldApplyFailOpenStrategy() {
        // Given: Config with FAIL_OPEN strategy and a broken storage provider
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test-limiter")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(10)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .failStrategy(RateLimitConfig.FailStrategy.FAIL_OPEN)
            .build();
        
        // Create engine with broken storage
        BrokenStorageProvider brokenStorage = new BrokenStorageProvider();
        LimiterEngine engineWithBrokenStorage = new LimiterEngine(
            brokenStorage, keyResolver, null, null
        );
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("test-key")
            .build();
        
        // When: Making request with broken storage
        RateLimitDecision decision = engineWithBrokenStorage.tryAcquire(context, config);
        
        // Then: Request allowed (fail open)
        assertTrue(decision.isAllowed());
    }
    
    @Test
    void shouldApplyFailClosedStrategy() {
        // Given: Config with FAIL_CLOSED strategy and a broken storage provider
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test-limiter")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(10)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .failStrategy(RateLimitConfig.FailStrategy.FAIL_CLOSED)
            .build();
        
        // Create engine with broken storage
        BrokenStorageProvider brokenStorage = new BrokenStorageProvider();
        LimiterEngine engineWithBrokenStorage = new LimiterEngine(
            brokenStorage, keyResolver, null, null
        );
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("test-key")
            .build();
        
        // When: Making request with broken storage
        RateLimitDecision decision = engineWithBrokenStorage.tryAcquire(context, config);
        
        // Then: Request denied (fail closed)
        assertFalse(decision.isAllowed());
        assertThat(decision.getReason()).contains("Rate limiter unavailable");
    }
    
    @Test
    void shouldHandleNullKeyGracefully() {
        // Given: Key resolver that returns null
        KeyResolver nullKeyResolver = context -> null;
        LimiterEngine engineWithNullKey = new LimiterEngine(
            storageProvider, nullKeyResolver, null, null
        );
        
        RateLimitConfig config = RateLimitConfig.builder()
            .name("test-limiter")
            .algorithm(RateLimitConfig.Algorithm.TOKEN_BUCKET)
            .requests(10)
            .window(60)
            .windowUnit(TimeUnit.SECONDS)
            .build();
        
        RateLimitContext context = RateLimitContext.builder()
            .keyExpression("test-key")
            .build();
        
        // When: Making request with null key
        RateLimitDecision decision = engineWithNullKey.tryAcquire(context, config);
        
        // Then: Should not crash, uses global-anonymous bucket
        assertNotNull(decision);
    }
    
    // ========== Helper Classes ==========
    
    /**
     * Storage provider that always throws exceptions.
     */
    private static class BrokenStorageProvider extends InMemoryStorageProvider {
        @Override
        public boolean tryAcquire(String key, RateLimitConfig config, long currentTime) {
            throw new RuntimeException("Storage is broken!");
        }
    }
}
