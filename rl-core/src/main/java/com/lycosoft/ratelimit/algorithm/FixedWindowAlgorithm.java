package com.lycosoft.ratelimit.algorithm;

/**
 * Fixed Window rate limiting algorithm.
 * 
 * <p>Divides time into fixed windows (e.g., 00:00-00:59, 01:00-01:59).
 * Each window has an independent counter that resets at the window boundary.
 * 
 * <p><b>Characteristics</b>:
 * <ul>
 *   <li>Simple and predictable</li>
 *   <li>Low memory overhead (one counter per window)</li>
 *   <li>Hard reset at window boundaries</li>
 *   <li>Potential for burst at window edges (2x limit)</li>
 * </ul>
 * 
 * <p><b>Edge Case - Burst Problem</b>:
 * <pre>
 * Window 1: [00:00 - 00:59]  Limit: 100
 * Window 2: [01:00 - 01:59]  Limit: 100
 * 
 * Scenario:
 * - 00:59:30 → 100 requests (allowed, within Window 1)
 * - 01:00:00 → 100 requests (allowed, within Window 2)
 * - Total in 30 seconds: 200 requests (2x the limit!)
 * </pre>
 * 
 * <p><b>Use Cases</b>:
 * <ul>
 *   <li>API rate limiting (GitHub, Twitter, Stripe)</li>
 *   <li>Simple quota enforcement</li>
 *   <li>Predictable reset times</li>
 * </ul>
 * 
 * <p><b>Algorithm</b>:
 * <pre>
 * 1. Calculate current window: floor(currentTime / windowSize)
 * 2. Get counter for this window
 * 3. Increment counter
 * 4. If counter {@code <=} limit: allow
 * 5. Else: deny
 * </pre>
 * 
 * @since 1.0.0
 */
public class FixedWindowAlgorithm {
    
    private final int windowSeconds;
    
    /**
     * Creates a fixed window algorithm instance.
     * 
     * @param windowSeconds window size in seconds
     */
    public FixedWindowAlgorithm(int windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        this.windowSeconds = windowSeconds;
    }
    
    /**
     * Attempts to acquire permission for a request.
     * 
     * @param state the current window state
     * @param limit maximum requests allowed per window
     * @param currentTime the current time in milliseconds
     * @return the new window state after attempting to acquire
     */
    public WindowState tryAcquire(WindowState state, int limit, long currentTime) {
        long currentWindow = calculateWindowNumber(currentTime);
        
        // Initialize state if first request or new window
        if (state == null || state.windowNumber != currentWindow) {
            state = new WindowState(currentWindow, 0);
        }
        
        // Check if limit would be exceeded
        if (state.requestCount >= limit) {
            return new WindowState(currentWindow, state.requestCount, false);
        }
        
        // Increment counter
        return new WindowState(currentWindow, state.requestCount + 1, true);
    }
    
    /**
     * Gets the remaining requests in the current window.
     * 
     * @param state the current window state
     * @param limit maximum requests allowed per window
     * @param currentTime the current time in milliseconds
     * @return number of remaining requests (0 if limit exceeded)
     */
    public int getRemaining(WindowState state, int limit, long currentTime) {
        long currentWindow = calculateWindowNumber(currentTime);
        
        if (state == null || state.windowNumber != currentWindow) {
            return limit;
        }
        
        return Math.max(0, limit - state.requestCount);
    }
    
    /**
     * Gets the time until the window resets.
     * 
     * @param currentTime the current time in milliseconds
     * @return seconds until reset
     */
    public long getResetTime(long currentTime) {
        long currentWindow = calculateWindowNumber(currentTime);
        long windowEndTime = (currentWindow + 1) * windowSeconds * 1000L;
        
        return Math.max(0, (windowEndTime - currentTime) / 1000);
    }
    
    /**
     * Calculates the window number for a given timestamp.
     * 
     * <p>Example:
     * <pre>
     * windowSeconds = 60
     * 
     * Time: 00:00:30 → window 0
     * Time: 00:01:15 → window 1
     * Time: 00:02:45 → window 2
     * </pre>
     * 
     * @param timestamp current timestamp in milliseconds
     * @return window number (0-based)
     */
    private long calculateWindowNumber(long timestamp) {
        return timestamp / (windowSeconds * 1000L);
    }
    
    /**
     * Represents the state of a fixed window.
     */
    public static class WindowState {
        private final long windowNumber;
        private final int requestCount;
        private final boolean allowed;
        
        public WindowState(long windowNumber, int requestCount) {
            this(windowNumber, requestCount, true);
        }
        
        public WindowState(long windowNumber, int requestCount, boolean allowed) {
            this.windowNumber = windowNumber;
            this.requestCount = requestCount;
            this.allowed = allowed;
        }
        
        public long getWindowNumber() {
            return windowNumber;
        }
        
        public int getRequestCount() {
            return requestCount;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
    }
}
