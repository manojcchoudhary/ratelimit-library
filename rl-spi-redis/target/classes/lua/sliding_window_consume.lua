-- Version: 1.0.0
-- Algorithm: Sliding Window Counter (Two-Window Weighted Average)
-- Description: Atomic sliding window rate limiting with O(1) memory

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_size_ms = tonumber(ARGV[2])
local current_time = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- Calculate current and previous window boundaries
local current_window_start = math.floor(current_time / window_size_ms) * window_size_ms
local previous_window_start = current_window_start - window_size_ms

-- Build keys for current and previous windows
local current_window_key = key .. ':' .. tostring(current_window_start)
local previous_window_key = key .. ':' .. tostring(previous_window_start)

-- Get counts from both windows
local current_count = tonumber(redis.call('GET', current_window_key)) or 0
local previous_count = tonumber(redis.call('GET', previous_window_key)) or 0

-- Calculate weighted rate
local time_elapsed_in_current = current_time - current_window_start
local overlap_weight = (window_size_ms - time_elapsed_in_current) / window_size_ms
local estimated_count = (previous_count * overlap_weight) + current_count

-- Decision: allow or deny
if estimated_count < limit then
    -- Request ALLOWED - increment current window
    redis.call('INCR', current_window_key)
    redis.call('EXPIRE', current_window_key, ttl)
    
    -- Return: {allowed=1, remaining=(limit - estimated_count - 1)}
    return {1, math.floor(limit - estimated_count - 1)}
else
    -- Request DENIED - no changes to counters
    -- Return: {allowed=0, remaining=0}
    return {0, 0}
end
