-- Version: 1.0.0
-- Algorithm: Fixed Window Counter
-- Description: Atomic fixed window rate limiting with O(1) memory
-- Note: Can allow 2x burst at window boundaries

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_size_ms = tonumber(ARGV[2])
local current_time = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- Calculate current window start time
local current_window_start = math.floor(current_time / window_size_ms) * window_size_ms

-- Build key for current window (includes window start for automatic rotation)
local window_key = key .. ':fw:' .. tostring(current_window_start)

-- Get current count
local current_count = tonumber(redis.call('GET', window_key)) or 0

-- Decision: allow or deny
if current_count < limit then
    -- Request ALLOWED - increment counter
    redis.call('INCR', window_key)
    redis.call('EXPIRE', window_key, ttl)

    -- Return: {allowed=1, remaining=(limit - current_count - 1)}
    return {1, limit - current_count - 1}
else
    -- Request DENIED - limit reached
    -- Return: {allowed=0, remaining=0}
    return {0, 0}
end
