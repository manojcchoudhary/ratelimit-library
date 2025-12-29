package com.lycosoft.ratelimit.example.springboot.model;

import java.time.LocalDateTime;

/**
 * Standard error response with rate limit information.
 * 
 * <p>Used by {@code GlobalExceptionHandler} to return consistent
 * error responses including rate limit details.
 */
public class ErrorResponse {
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private RateLimitInfo rateLimitInfo;
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and Setters
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public RateLimitInfo getRateLimitInfo() {
        return rateLimitInfo;
    }
    
    public void setRateLimitInfo(RateLimitInfo rateLimitInfo) {
        this.rateLimitInfo = rateLimitInfo;
    }
    
    // Nested RateLimitInfo class
    public static class RateLimitInfo {
        private String limiter;
        private int limit;
        private int remaining;
        private long resetAt;
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters and Setters
        public String getLimiter() {
            return limiter;
        }
        
        public void setLimiter(String limiter) {
            this.limiter = limiter;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public void setLimit(int limit) {
            this.limit = limit;
        }
        
        public int getRemaining() {
            return remaining;
        }
        
        public void setRemaining(int remaining) {
            this.remaining = remaining;
        }
        
        public long getResetAt() {
            return resetAt;
        }
        
        public void setResetAt(long resetAt) {
            this.resetAt = resetAt;
        }
        
        // Builder for RateLimitInfo
        public static class Builder {
            private String limiter;
            private int limit;
            private int remaining;
            private long resetAt;
            
            public Builder limiter(String limiter) {
                this.limiter = limiter;
                return this;
            }
            
            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }
            
            public Builder remaining(int remaining) {
                this.remaining = remaining;
                return this;
            }
            
            public Builder resetAt(long resetAt) {
                this.resetAt = resetAt;
                return this;
            }
            
            public RateLimitInfo build() {
                RateLimitInfo info = new RateLimitInfo();
                info.limiter = this.limiter;
                info.limit = this.limit;
                info.remaining = this.remaining;
                info.resetAt = this.resetAt;
                return info;
            }
        }
    }
    
    // Builder for ErrorResponse
    public static class Builder {
        private String error;
        private String message;
        private String path;
        private LocalDateTime timestamp;
        private RateLimitInfo rateLimitInfo;
        
        public Builder error(String error) {
            this.error = error;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder rateLimitInfo(RateLimitInfo rateLimitInfo) {
            this.rateLimitInfo = rateLimitInfo;
            return this;
        }
        
        public ErrorResponse build() {
            ErrorResponse response = new ErrorResponse();
            response.error = this.error;
            response.message = this.message;
            response.path = this.path;
            response.timestamp = this.timestamp != null ? this.timestamp : LocalDateTime.now();
            response.rateLimitInfo = this.rateLimitInfo;
            return response;
        }
    }
}
