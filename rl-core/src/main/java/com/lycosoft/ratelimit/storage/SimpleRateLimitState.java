package com.lycosoft.ratelimit.storage;

import com.lycosoft.ratelimit.spi.RateLimitState;

public class SimpleRateLimitState implements RateLimitState {
    private final int limit;
    private final int remaining;
    private final long resetTime;
    private final int currentUsage;

    public SimpleRateLimitState(int limit, int remaining, long resetTime, int currentUsage) {
        this.limit = limit;
        this.remaining = remaining;
        this.resetTime = resetTime;
        this.currentUsage = currentUsage;
    }

    @Override public int getLimit() { return limit; }
    @Override public int getRemaining() { return remaining; }
    @Override public long getResetTime() { return resetTime; }
    @Override public int getCurrentUsage() { return currentUsage; }

    // Add builder for clarity
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int limit = 0;
        private int remaining = 0;
        private long resetTime = 0;
        private int currentUsage = 0;

        public Builder limit(int limit) { this.limit = limit; return this; }
        public Builder remaining(int remaining) { this.remaining = remaining; return this; }
        public Builder resetTime(long resetTime) { this.resetTime = resetTime; return this; }
        public Builder currentUsage(int currentUsage) { this.currentUsage = currentUsage; return this; }

        public SimpleRateLimitState build() {
            return new SimpleRateLimitState(limit, remaining, resetTime, currentUsage);
        }
    }
}