package com.lycosoft.ratelimit.example.springboot.model;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper.
 * 
 * <p>Provides consistent response structure across all endpoints.
 */
public class ApiResponse {
    private String message;
    private LocalDateTime timestamp;
    private Object data;
    
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ApiResponse(String message, Object data) {
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.data = data;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and Setters
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    // Builder
    public static class Builder {
        private String message;
        private LocalDateTime timestamp;
        private Object data;
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder data(Object data) {
            this.data = data;
            return this;
        }
        
        public ApiResponse build() {
            ApiResponse response = new ApiResponse();
            response.message = this.message;
            response.timestamp = this.timestamp != null ? this.timestamp : LocalDateTime.now();
            response.data = this.data;
            return response;
        }
    }
}
