package com.lycosoft.ratelimit.spi;

import com.lycosoft.ratelimit.engine.RateLimitContext;

/**
 * Service Provider Interface for custom variable providers.
 * 
 * <p>Variable providers allow extending the set of variables available in
 * key expressions (SpEL, EL) beyond the built-in ones (#user, #ip, #args, #headers).
 * 
 * <p><b>Security:</b> Custom variables MUST be validated before registration.
 * The variable name is checked against forbidden keywords to prevent security violations.
 * 
 * <p><b>Example Use Cases:</b>
 * <ul>
 *   <li>#tenant - Multi-tenancy support</li>
 *   <li>#apiKey - API key-based rate limiting</li>
 *   <li>#deviceId - Mobile device identification</li>
 *   <li>#country - Geographic rate limiting</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public interface VariableProvider {
    
    /**
     * Returns the name of the variable (without the '#' prefix).
     * 
     * <p>Example: "tenant", "apiKey", "deviceId"
     * 
     * <p><b>Security:</b> Variable names are validated against forbidden keywords:
     * <ul>
     *   <li>class, forName, classLoader, loader</li>
     *   <li>runtime, system, process</li>
     *   <li>method, constructor, field, invoke</li>
     *   <li>securityManager, accessController</li>
     *   <li>file, socket, url</li>
     * </ul>
     * 
     * @return the variable name (must be alphanumeric, no special characters)
     */
    String getVariableName();
    
    /**
     * Resolves the value of this variable for the given context.
     * 
     * <p>This method is called for every request where the variable is referenced,
     * so it must be fast (ideally <1ms).
     * 
     * @param context the current request context
     * @return the resolved value (never null; return empty string if unavailable)
     */
    Object resolveValue(RateLimitContext context);
    
    /**
     * Returns the Java type of this variable's value.
     * 
     * <p>This is used for type checking in expression evaluation.
     * 
     * @return the variable type (e.g., String.class, Integer.class)
     */
    Class<?> getVariableType();
    
    /**
     * Validates that this variable provider is safe to use.
     * 
     * <p>Default implementation checks variable name against forbidden keywords.
     * Override to add additional validation.
     * 
     * @throws VariableValidationException if validation fails
     */
    default void validate() throws VariableValidationException {
        String name = getVariableName();
        
        if (name == null || name.trim().isEmpty()) {
            throw new VariableValidationException("Variable name cannot be null or empty");
        }
        
        if (!name.matches("[a-zA-Z][a-zA-Z0-9]*")) {
            throw new VariableValidationException(
                "Variable name must be alphanumeric and start with a letter: " + name
            );
        }
        
        // Check against forbidden keywords
        String lowerName = name.toLowerCase();
        String[] forbiddenKeywords = {
            "class", "forname", "classloader", "loader",
            "runtime", "system", "process", "processbuilder",
            "method", "constructor", "field", "invoke",
            "securitymanager", "accesscontroller",
            "file", "socket", "url"
        };
        
        for (String forbidden : forbiddenKeywords) {
            if (lowerName.contains(forbidden)) {
                throw new VariableValidationException(
                    "Variable name contains forbidden keyword '" + forbidden + "': " + name
                );
            }
        }
    }
    
    /**
     * Exception thrown when variable validation fails.
     */
    class VariableValidationException extends RuntimeException {
        public VariableValidationException(String message) {
            super(message);
        }
    }
}
