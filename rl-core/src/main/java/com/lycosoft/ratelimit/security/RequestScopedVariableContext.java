package com.lycosoft.ratelimit.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-local storage for request-scoped variables.
 * 
 * <p>This class provides a thread-safe way to store variables that are only
 * valid for the duration of a single request. Variables are automatically
 * cleaned up after request processing.
 * 
 * <p><b>Critical:</b> Always call {@link #clear()} in a finally block to prevent
 * memory leaks and ensure proper cleanup.
 * 
 * <p><b>Usage Pattern:</b>
 * <pre>{@code
 * try {
 *     RequestScopedVariableContext.setVariable("user", getCurrentUser());
 *     RequestScopedVariableContext.setVariable("ip", getRemoteAddress());
 *     
 *     // Process rate limiting
 *     rateLimiter.checkLimit();
 * } finally {
 *     RequestScopedVariableContext.clear();  // MANDATORY
 * }
 * }</pre>
 * 
 * @since 1.0.0
 */
public class RequestScopedVariableContext {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestScopedVariableContext.class);
    
    /**
     * Thread-local storage for request variables.
     */
    private static final ThreadLocal<Map<String, Object>> CONTEXT = 
        ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Thread-local flag to track if context has been initialized.
     */
    private static final ThreadLocal<Boolean> INITIALIZED = 
        ThreadLocal.withInitial(() -> false);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private RequestScopedVariableContext() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Sets a variable in the current request context.
     * 
     * @param name the variable name
     * @param value the variable value (null values are allowed)
     * @throws IllegalArgumentException if name is null or empty
     */
    public static void setVariable(String name, Object value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Variable name cannot be null or empty");
        }
        
        Map<String, Object> context = CONTEXT.get();
        Object oldValue = context.put(name, value);
        
        if (!INITIALIZED.get()) {
            INITIALIZED.set(true);
            logger.debug("Initialized request-scoped context");
        }
        
        if (logger.isTraceEnabled()) {
            if (oldValue != null) {
                logger.trace("Updated variable: {} (old={}, new={})", name, oldValue, value);
            } else {
                logger.trace("Set variable: {} = {}", name, value);
            }
        }
    }
    
    /**
     * Gets a variable from the current request context.
     * 
     * @param name the variable name
     * @return the variable value, or null if not found
     */
    public static Object getVariable(String name) {
        if (name == null) {
            return null;
        }
        return CONTEXT.get().get(name);
    }
    
    /**
     * Gets a variable with a specific type.
     * 
     * @param name the variable name
     * @param type the expected type
     * @param <T> the type parameter
     * @return the variable value cast to the specified type, or null if not found
     * @throws ClassCastException if the variable exists but is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public static <T> T getVariable(String name, Class<T> type) {
        Object value = getVariable(name);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                "Variable '" + name + "' is of type " + value.getClass().getName() +
                ", not " + type.getName()
            );
        }
        return (T) value;
    }
    
    /**
     * Removes a variable from the current request context.
     * 
     * @param name the variable name
     * @return the previous value, or null if not found
     */
    public static Object removeVariable(String name) {
        if (name == null) {
            return null;
        }
        Object removed = CONTEXT.get().remove(name);
        if (logger.isTraceEnabled() && removed != null) {
            logger.trace("Removed variable: {} (was: {})", name, removed);
        }
        return removed;
    }
    
    /**
     * Checks if a variable exists in the current request context.
     * 
     * @param name the variable name
     * @return true if the variable exists (even if null), false otherwise
     */
    public static boolean hasVariable(String name) {
        if (name == null) {
            return false;
        }
        return CONTEXT.get().containsKey(name);
    }
    
    /**
     * Gets all variables in the current request context.
     * 
     * @return a copy of the current context map
     */
    public static Map<String, Object> getAllVariables() {
        return new HashMap<>(CONTEXT.get());
    }
    
    /**
     * Gets the number of variables in the current request context.
     * 
     * @return the number of variables
     */
    public static int size() {
        return CONTEXT.get().size();
    }
    
    /**
     * Checks if the current request context is empty.
     * 
     * @return true if no variables are set, false otherwise
     */
    public static boolean isEmpty() {
        return CONTEXT.get().isEmpty();
    }
    
    /**
     * Clears all variables from the current request context.
     * 
     * <p><b>CRITICAL:</b> This method MUST be called in a finally block
     * to prevent memory leaks in thread pools.
     * 
     * <p>Example:
     * <pre>{@code
     * try {
     *     // Set variables and process request
     * } finally {
     *     RequestScopedVariableContext.clear();  // MANDATORY
     * }
     * }</pre>
     */
    public static void clear() {
        Map<String, Object> context = CONTEXT.get();
        int size = context.size();
        context.clear();
        INITIALIZED.remove();
        
        if (logger.isTraceEnabled() && size > 0) {
            logger.trace("Cleared {} variables from request context", size);
        }
    }
    
    /**
     * Clears the ThreadLocal completely.
     * 
     * <p>This is more aggressive than {@link #clear()} and should only be
     * used when the thread is being shut down or returned to a pool.
     * 
     * <p>This method removes the ThreadLocal entry entirely, which can help
     * prevent memory leaks in long-lived thread pools.
     */
    public static void remove() {
        clear();
        CONTEXT.remove();
        if (logger.isTraceEnabled()) {
            logger.trace("Removed ThreadLocal context");
        }
    }
}
