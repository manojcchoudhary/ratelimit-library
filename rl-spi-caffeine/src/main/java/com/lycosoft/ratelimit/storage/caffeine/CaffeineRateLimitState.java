package com.lycosoft.ratelimit.storage.caffeine;

import com.lycosoft.ratelimit.spi.RateLimitState;
import com.lycosoft.ratelimit.storage.SimpleRateLimitState;

/**
 * Caffeine-specific rate limit state.
 */
class CaffeineRateLimitState extends SimpleRateLimitState {

    CaffeineRateLimitState(int limit, int remaining, long resetTime, int currentUsage) {
        super(limit, remaining, resetTime, currentUsage);
    }

}
