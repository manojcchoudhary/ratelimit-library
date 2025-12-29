package com.lycosoft.ratelimit.algorithm;

/**
 * Sliding Window Counter Algorithm implementation.
 * 
 * <p>Uses a weighted moving average between the current and previous time windows
 * to provide high accuracy without the O(N) memory overhead of a sliding window log.
 * 
 * <p><b>Mathematical Model:</b>
 * <pre>
 * Rate = (Previous_Count × Overlap_Weight) + Current_Count
 * 
 * Where:
 *   Overlap_Weight = (Window_Size - Time_Elapsed_In_Current) / Window_Size
 * </pre>
 * 
 * <p><b>Design Decisions:</b>
 * <ul>
 *   <li><b>Granularity:</b> Configurable (seconds, minutes, hours)</li>
 *   <li><b>Precision:</b> Two-Window Weighted Average</li>
 *   <li><b>Memory Complexity:</b> O(1) per user (only 2 windows)</li>
 *   <li><b>Memory Safety:</b> Minimum time unit = 1 second</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class SlidingWindowAlgorithm {
    
    private final int limit;
    private final long windowSizeMs;
    
    /**
     * Creates a Sliding Window Counter algorithm instance.
     * 
     * @param limit the maximum number of requests allowed in the window
     * @param windowSizeMs the window size in milliseconds (minimum 1000ms)
     */
    public SlidingWindowAlgorithm(int limit, long windowSizeMs) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (windowSizeMs < 1000) {
            throw new IllegalArgumentException("windowSizeMs must be at least 1000 (1 second)");
        }
        
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
    }
    
    /**
     * Attempts to consume a slot in the current window.
     * 
     * @param state the current window state
     * @param currentTime the current time in milliseconds
     * @return the new window state after attempting to consume
     */
    public WindowState tryConsume(WindowState state, long currentTime) {
        // Determine current window boundaries
        long currentWindowStart = (currentTime / windowSizeMs) * windowSizeMs;
        long previousWindowStart = currentWindowStart - windowSizeMs;
        
        // Initialize or rotate windows
        if (state == null || state.currentWindow == null 
                || state.currentWindow.windowStart < currentWindowStart) {
            // Window rotation needed
            WindowData previousWindow = (state != null && state.currentWindow != null 
                    && state.currentWindow.windowStart == previousWindowStart)
                    ? state.currentWindow
                    : null;
            
            WindowData currentWindow = new WindowData(currentWindowStart, 0);
            state = new WindowState(currentWindow, previousWindow);
        }
        
        // Calculate weighted rate
        long timeElapsedInCurrent = currentTime - currentWindowStart;
        double overlapWeight = (windowSizeMs - timeElapsedInCurrent) / (double) windowSizeMs;
        
        int previousCount = (state.previousWindow != null) ? state.previousWindow.count : 0;
        double estimatedCount = (previousCount * overlapWeight) + state.currentWindow.count;
        
        // Decision: allow or deny
        if (estimatedCount < limit) {
            // Request ALLOWED - increment current window
            WindowData newCurrentWindow = new WindowData(
                state.currentWindow.windowStart,
                state.currentWindow.count + 1
            );
            return new WindowState(newCurrentWindow, state.previousWindow, true);
        } else {
            // Request DENIED - no change to counters
            return new WindowState(state.currentWindow, state.previousWindow, false);
        }
    }
    
    /**
     * Represents the state of sliding windows.
     */
    public static class WindowState {
        private final WindowData currentWindow;
        private final WindowData previousWindow;
        private final boolean allowed;
        
        public WindowState(WindowData currentWindow, WindowData previousWindow) {
            this(currentWindow, previousWindow, true);
        }
        
        public WindowState(WindowData currentWindow, WindowData previousWindow, boolean allowed) {
            this.currentWindow = currentWindow;
            this.previousWindow = previousWindow;
            this.allowed = allowed;
        }
        
        public WindowData getCurrentWindow() {
            return currentWindow;
        }
        
        public WindowData getPreviousWindow() {
            return previousWindow;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        @Override
        public String toString() {
            return "WindowState{" +
                    "currentWindow=" + currentWindow +
                    ", previousWindow=" + previousWindow +
                    ", allowed=" + allowed +
                    '}';
        }
    }
    
    /**
     * Represents a single time window with its count.
     */
    public static class WindowData {
        private final long windowStart;
        private final int count;
        
        public WindowData(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
        
        public long getWindowStart() {
            return windowStart;
        }
        
        public int getCount() {
            return count;
        }
        
        @Override
        public String toString() {
            return "WindowData{" +
                    "windowStart=" + windowStart +
                    ", count=" + count +
                    '}';
        }
    }
}
