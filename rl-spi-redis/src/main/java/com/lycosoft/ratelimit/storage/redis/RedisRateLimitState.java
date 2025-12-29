package com.lycosoft.ratelimit.storage.redis;

import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.storage.SimpleRateLimitState;

/**
 * Redis-specific rate limit state.
 */
class RedisRateLimitState extends SimpleRateLimitState {

    RedisRateLimitState(int limit, int remaining, long resetTime, int currentUsage) {
        super(limit, remaining, resetTime, currentUsage);
    }

}
