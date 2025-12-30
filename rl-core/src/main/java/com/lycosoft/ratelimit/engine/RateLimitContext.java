package com.lycosoft.ratelimit.engine;

import com.lycosoft.ratelimit.constants.RateLimitDefaultValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Context object containing all information needed to resolve a rate limit key
 * and make a rate limiting decision.
 * 
 * <p>This is an immutable value object that captures the current request state.
 * 
 * @since 1.0.0
 */
public final class RateLimitContext {
    
    private final String keyExpression;
    private final Object principal;
    private final String remoteAddress;
    private final Object[] methodArguments;
    private final Map<String, String> requestHeaders;
    private final String methodSignature;
    
    private RateLimitContext(Builder builder) {
        this.keyExpression = builder.keyExpression;
        this.principal = builder.principal;
        this.remoteAddress = builder.remoteAddress;
        // Defensive copy to prevent external mutation of internal state
        this.methodArguments = builder.methodArguments != null
            ? Arrays.copyOf(builder.methodArguments, builder.methodArguments.length)
            : new Object[0];
        // Defensive copy to prevent external mutation of internal state
        this.requestHeaders = builder.requestHeaders != null
            ? Map.copyOf(builder.requestHeaders)
            : Collections.emptyMap();
        this.methodSignature = builder.methodSignature;
    }
    
    public String getKeyExpression() {
        return keyExpression;
    }
    
    public Object getPrincipal() {
        return principal;
    }
    
    public String getRemoteAddress() {
        return remoteAddress;
    }
    
    /**
     * Returns a defensive copy of the method arguments array.
     *
     * <p><b>Encapsulation:</b> Returns a copy to prevent external mutation
     * of this immutable object's internal state.
     *
     * @return a copy of the method arguments array (never null)
     */
    public Object[] getMethodArguments() {
        return Arrays.copyOf(methodArguments, methodArguments.length);
    }
    
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }
    
    public String getMethodSignature() {
        return methodSignature;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String keyExpression = RateLimitDefaultValue.KEY_EXPRESSION;
        private Object principal;
        private String remoteAddress;
        private Object[] methodArguments;
        private Map<String, String> requestHeaders;
        private String methodSignature;
        
        public Builder keyExpression(String keyExpression) {
            this.keyExpression = keyExpression;
            return this;
        }
        
        public Builder principal(Object principal) {
            this.principal = principal;
            return this;
        }
        
        public Builder remoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }
        
        public Builder methodArguments(Object[] methodArguments) {
            this.methodArguments = methodArguments;
            return this;
        }
        
        public Builder requestHeaders(Map<String, String> requestHeaders) {
            this.requestHeaders = requestHeaders;
            return this;
        }
        
        public Builder methodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
            return this;
        }
        
        public RateLimitContext build() {
            Objects.requireNonNull(keyExpression, "keyExpression cannot be null");
            
            // Set defaults
            if (methodArguments == null) {
                methodArguments = new Object[0];
            }
            if (remoteAddress == null) {
                remoteAddress = "unknown";
            }
            
            return new RateLimitContext(this);
        }
    }
    
    @Override
    public String toString() {
        return "RateLimitContext{" +
                "keyExpression='" + keyExpression + '\'' +
                ", principal=" + principal +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", methodSignature='" + methodSignature + '\'' +
                '}';
    }
}
