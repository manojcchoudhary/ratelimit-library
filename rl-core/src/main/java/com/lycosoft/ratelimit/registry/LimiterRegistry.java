package com.lycosoft.ratelimit.registry;

import com.lycosoft.ratelimit.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing the lifecycle of rate limiters.
 * 
 * <p>This class maintains a thread-safe registry of all active rate limiters
 * and their configurations. It supports:
 * <ul>
 *   <li>Registration of new rate limiters</li>
 *   <li>Retrieval of existing configurations</li>
 *   <li>Deregistration (cleanup)</li>
 *   <li>Enumeration of all limiters</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is thread-safe and can be shared across requests.
 * 
 * @since 1.0.0
 */
public class LimiterRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(LimiterRegistry.class);
    
    private final ConcurrentHashMap<String, RateLimitConfig> limiters = new ConcurrentHashMap<>();
    
    /**
     * Registers a new rate limiter configuration.
     * 
     * <p>If a limiter with the same name already exists, it will be replaced
     * and a warning will be logged.
     * 
     * @param config the rate limit configuration
     * @return the previous configuration if one existed, or null
     */
    public RateLimitConfig register(RateLimitConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(config.getName(), "config name cannot be null");
        
        RateLimitConfig previous = limiters.put(config.getName(), config);
        
        if (previous != null) {
            logger.warn("Replacing existing rate limiter: {} (old: {}, new: {})", 
                config.getName(), previous, config);
        } else {
            logger.info("Registered rate limiter: {} ({})", config.getName(), config);
        }
        
        return previous;
    }
    
    /**
     * Retrieves the configuration for a rate limiter by name.
     * 
     * @param name the name of the rate limiter
     * @return the configuration, or empty if not found
     */
    public Optional<RateLimitConfig> get(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return Optional.ofNullable(limiters.get(name));
    }
    
    /**
     * Checks if a rate limiter with the given name is registered.
     * 
     * @param name the name of the rate limiter
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return limiters.containsKey(name);
    }
    
    /**
     * Deregisters a rate limiter by name.
     * 
     * @param name the name of the rate limiter
     * @return the configuration that was deregistered, or empty if not found
     */
    public Optional<RateLimitConfig> deregister(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        
        RateLimitConfig removed = limiters.remove(name);
        
        if (removed != null) {
            logger.info("Deregistered rate limiter: {}", name);
        }
        
        return Optional.ofNullable(removed);
    }
    
    /**
     * Deregisters all rate limiters.
     */
    public void clear() {
        int count = limiters.size();
        limiters.clear();
        logger.info("Cleared all rate limiters (count: {})", count);
    }
    
    /**
     * Returns an unmodifiable view of all registered rate limiters.
     * 
     * @return a map of limiter name to configuration
     */
    public Map<String, RateLimitConfig> getAll() {
        return Collections.unmodifiableMap(limiters);
    }
    
    /**
     * Returns the number of registered rate limiters.
     * 
     * @return the count
     */
    public int size() {
        return limiters.size();
    }
    
    @Override
    public String toString() {
        return "LimiterRegistry{" +
                "size=" + limiters.size() +
                ", limiters=" + limiters.keySet() +
                '}';
    }
}
