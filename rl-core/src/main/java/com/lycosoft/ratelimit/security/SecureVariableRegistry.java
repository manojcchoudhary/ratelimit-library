package com.lycosoft.ratelimit.security;

import com.lycosoft.ratelimit.spi.VariableProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for custom variables with security validation.
 * 
 * <p>This registry ensures that only safe variables can be registered
 * and used in rate limiting expressions.
 * 
 * <p><b>Security:</b> All variable registrations are validated before being accepted.
 * 
 * @since 1.0.0
 */
public class SecureVariableRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(SecureVariableRegistry.class);
    
    private final Map<String, VariableProvider> providers;
    private final VariableValidator validator;
    private final int maxVariables;
    
    /**
     * Creates a new secure variable registry with default settings.
     */
    public SecureVariableRegistry() {
        this(100); // Default max 100 custom variables
    }
    
    /**
     * Creates a new secure variable registry with specified maximum.
     * 
     * @param maxVariables the maximum number of variables that can be registered
     */
    public SecureVariableRegistry(int maxVariables) {
        this.providers = new ConcurrentHashMap<>();
        this.validator = new VariableValidator();
        this.maxVariables = maxVariables;
    }
    
    /**
     * Registers a variable provider.
     * 
     * <p>The provider's variable name and type are validated before registration.
     * 
     * @param provider the variable provider to register
     * @throws SecurityException if the provider fails security validation
     * @throws IllegalStateException if the maximum number of variables has been reached
     * @throws IllegalArgumentException if a provider with the same name is already registered
     */
    public void register(VariableProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        
        String variableName = provider.getVariableName();
        
        // Validate variable name
        validator.validateVariableName(variableName);
        
        // Validate variable type
        Class<?> variableType = provider.getVariableType();
        if (!validator.isSafeType(variableType)) {
            throw new SecurityException(
                "Variable '" + variableName + "' has unsafe type: " + variableType.getName()
            );
        }
        
        // Check capacity
        if (providers.size() >= maxVariables && !providers.containsKey(variableName)) {
            throw new IllegalStateException(
                "Maximum number of variables (" + maxVariables + ") reached"
            );
        }
        
        // Register
        VariableProvider existing = providers.putIfAbsent(variableName, provider);
        if (existing != null && existing != provider) {
            throw new IllegalArgumentException(
                "Variable '" + variableName + "' is already registered"
            );
        }
        
        logger.info("Registered variable: {} (type: {})", variableName, variableType.getSimpleName());
    }
    
    /**
     * Unregisters a variable provider.
     * 
     * @param variableName the name of the variable to unregister
     * @return the unregistered provider, or null if not found
     */
    public VariableProvider unregister(String variableName) {
        VariableProvider removed = providers.remove(variableName);
        if (removed != null) {
            logger.info("Unregistered variable: {}", variableName);
        }
        return removed;
    }
    
    /**
     * Gets a variable provider by name.
     * 
     * @param variableName the variable name
     * @return the provider, or null if not found
     */
    public VariableProvider getProvider(String variableName) {
        return providers.get(variableName);
    }
    
    /**
     * Checks if a variable is registered.
     * 
     * @param variableName the variable name
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(String variableName) {
        return providers.containsKey(variableName);
    }
    
    /**
     * Gets all registered variable names.
     * 
     * @return a copy of the registered variable names
     */
    public Set<String> getRegisteredVariables() {
        return new HashSet<>(providers.keySet());
    }
    
    /**
     * Gets the number of registered variables.
     * 
     * @return the count of registered variables
     */
    public int size() {
        return providers.size();
    }
    
    /**
     * Clears all registered variables.
     */
    public void clear() {
        int count = providers.size();
        providers.clear();
        logger.info("Cleared {} registered variables", count);
    }
    
    /**
     * Gets the validator used by this registry.
     * 
     * @return the variable validator
     */
    public VariableValidator getValidator() {
        return validator;
    }
}
