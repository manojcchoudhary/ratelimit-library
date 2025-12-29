-- Version: 1.0.0
-- Algorithm: Token Bucket (Lazy Refill)
-- Description: Atomic token bucket rate limiting with lazy refill strategy

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])  -- tokens per millisecond
local tokens_required = tonumber(ARGV[3])
local current_time = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

-- Get current state from Redis
-- Returns: {tokens, last_refill_time}
local state = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(state[1]) or capacity  -- If nil, bucket starts FULL
local last_refill = tonumber(state[2]) or current_time

-- Lazy refill calculation
local elapsed = current_time - last_refill
local tokens_to_add = elapsed * refill_rate
local available = math.min(capacity, tokens + tokens_to_add)

-- Binary decision: all-or-nothing
if available >= tokens_required then
    -- Request ALLOWED - consume tokens
    redis.call('HSET', key, 
        'tokens', available - tokens_required, 
        'last_refill', current_time)
    redis.call('EXPIRE', key, ttl)
    
    -- Return: {allowed=1, remaining_tokens}
    return {1, available - tokens_required}
else
    -- Request DENIED - update available tokens (refill happened) but not last_refill
    redis.call('HSET', key, 'tokens', available)
    -- Note: Don't update last_refill since no consumption occurred
    redis.call('EXPIRE', key, ttl)
    
    -- Return: {allowed=0, remaining_tokens}
    return {0, available}
end
