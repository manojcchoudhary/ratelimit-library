package com.lycosoft.ratelimit.algorithm;

import org.jetbrains.annotations.NotNull;

/**
 * Token Bucket Algorithm implementation.
 *
 * <p>This algorithm allows for smooth bursts by maintaining a "bucket" of tokens
 * that refills at a fixed rate. Each request consumes tokens from the bucket.
 *
 * <p><b>Mathematical Model:</b>
 * <pre>
 * T_available = min(B, T_last + (t_current - t_last) × R)
 *
 * Where:
 *   B         = Bucket Capacity (max tokens)
 *   R         = Refill Rate (tokens per millisecond)
 *   T_last    = Token count at last request
 *   t_current = Current timestamp
 *   t_last    = Timestamp of last request
 * </pre>
 *
 * <p><b>Design Decisions:</b>
 * <ul>
 *   <li><b>Request Fulfillment:</b> Strictly Binary (all-or-nothing)</li>
 *   <li><b>Refill Strategy:</b> Continuous (Lazy Calculation)</li>
 *   <li><b>Initial State:</b> Buckets start FULL</li>
 * </ul>
 *
 * <p><b>Performance:</b> O(1) time complexity, no background threads.
 *
 * @since 1.0.0
 */
public class TokenBucketAlgorithm {

    private final double capacity;
    private final double refillRate; // tokens per millisecond

    /**
     * Creates a Token Bucket algorithm instance.
     *
     * @param capacity   the maximum number of tokens (bucket capacity)
     * @param refillRate the rate at which tokens are added (tokens per millisecond)
     */
    public TokenBucketAlgorithm(double capacity, double refillRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillRate <= 0) {
            throw new IllegalArgumentException("refillRate must be positive");
        }

        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    /**
     * Attempts to consume tokens from the bucket.
     *
     * <p>This method uses lazy refill calculation - tokens are recalculated
     * at request time based on elapsed time since the last request.
     *
     * @param state          the current bucket state
     * @param tokensRequired the number of tokens required for this request
     * @param currentTime    the current time in milliseconds
     * @return the new bucket state after attempting to consume tokens
     */
    public BucketState tryConsume(BucketState state, int tokensRequired, long currentTime) {
        if (tokensRequired <= 0) {
            throw new IllegalArgumentException("tokensRequired must be positive");
        }

        // Initialize state if first request (bucket starts FULL)
        if (state == null) {
            state = new BucketState(capacity, currentTime);
        }

        // Lazy refill calculation
        long elapsedTime = currentTime - state.lastRefillTime;
        double tokensToAdd = elapsedTime * refillRate;
        double availableTokens = Math.min(capacity, state.tokens + tokensToAdd);

        // Binary decision: all-or-nothing
        if (availableTokens >= tokensRequired) {
            // Consume tokens - request ALLOWED
            return new BucketState(
                    availableTokens - tokensRequired,
                    currentTime,
                    true
            );
        } else {
            // Not enough tokens - request DENIED
            // Note: We still update availableTokens (refill happened)
            // but don't update lastRefillTime (no consumption occurred)
            return new BucketState(
                    availableTokens,
                    currentTime,
                    false
            );
        }
    }

    /**
     * Represents the state of a token bucket.
     */
    public record BucketState(double tokens, long lastRefillTime, boolean allowed) {
        /**
         * Creates initial bucket state (full).
         */
        public BucketState(double tokens, long lastRefillTime) {
            this(tokens, lastRefillTime, true);
        }

        /**
         * Creates bucket state with decision.
         */
        public BucketState {
        }

        @Override
        @NotNull
        public String toString() {
            return "BucketState{" +
                    "tokens=" + tokens +
                    ", lastRefillTime=" + lastRefillTime +
                    ", allowed=" + allowed +
                    '}';
        }
    }
}
