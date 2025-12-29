package com.lycosoft.ratelimit.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates variable names and values to prevent security vulnerabilities.
 * 
 * <p>This validator protects against:
 * <ul>
 *   <li>SpEL injection attacks</li>
 *   <li>ClassLoader access</li>
 *   <li>Runtime/System access</li>
 *   <li>Reflection abuse</li>
 * </ul>
 * 
 * <p><b>Security Critical:</b> This class is essential for preventing
 * Remote Code Execution (RCE) attacks when using expression languages.
 * 
 * @since 1.0.0
 */
public class VariableValidator {
    
    /**
     * Forbidden keywords that indicate potential security risks.
     * These keywords are commonly used in injection attacks.
     */
    private static final Set<String> FORBIDDEN_KEYWORDS = new HashSet<>(Arrays.asList(
        // ClassLoader and Class access
        "class", "forname", "classloader", "loader",
        
        // Runtime and System access
        "runtime", "system", "process", "processbuilder",
        
        // Reflection
        "method", "constructor", "field", "invoke",
        
        // Security Manager
        "securitymanager", "accesscontroller",
        
        // File and Network I/O
        "file", "socket", "url",
        
        // Script engines
        "scriptengine", "scriptenginemanager"
    ));
    
    /**
     * Pattern for valid variable names.
     * Must start with letter, can contain letters, digits, and underscore.
     */
    private static final Pattern VALID_VARIABLE_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    
    /**
     * Pattern to detect potential injection attempts in variable names.
     */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
        ".*[\\(\\)\\{\\}\\[\\]<>;\\.\\$#@!].*"
    );
    
    /**
     * Validates a variable name for security.
     * 
     * @param variableName the variable name to validate
     * @throws SecurityException if the variable name is invalid or contains forbidden keywords
     */
    public void validateVariableName(String variableName) {
        if (variableName == null || variableName.trim().isEmpty()) {
            throw new SecurityException("Variable name cannot be null or empty");
        }
        
        String normalized = variableName.trim().toLowerCase();
        
        // Check for forbidden keywords
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalized.contains(keyword)) {
                throw new SecurityException(
                    "Variable name '" + variableName + "' contains forbidden keyword: " + keyword
                );
            }
        }
        
        // Check for injection characters
        if (INJECTION_PATTERN.matcher(variableName).matches()) {
            throw new SecurityException(
                "Variable name '" + variableName + "' contains suspicious characters"
            );
        }
        
        // Check valid format
        if (!VALID_VARIABLE_NAME.matcher(variableName).matches()) {
            throw new SecurityException(
                "Variable name '" + variableName + "' does not match valid format: " +
                "must start with letter, can contain letters, digits, and underscore"
            );
        }
    }
    
    /**
     * Validates a variable value for security.
     * 
     * <p>This method performs basic validation to detect obvious injection attempts.
     * 
     * @param variableName the variable name (for error messages)
     * @param value the value to validate
     * @throws SecurityException if the value appears to contain malicious content
     */
    public void validateVariableValue(String variableName, Object value) {
        if (value == null) {
            return; // null is safe
        }
        
        String valueStr = value.toString().toLowerCase();
        
        // Check for forbidden keywords in value
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (valueStr.contains(keyword)) {
                throw new SecurityException(
                    "Variable '" + variableName + "' value contains forbidden keyword: " + keyword
                );
            }
        }
        
        // Check for suspicious patterns that might indicate code injection
        if (valueStr.contains("t(") || // T() expression in SpEL
            valueStr.contains("@") ||  // Bean references in SpEL
            valueStr.contains("${") || // Property placeholders
            valueStr.contains("#{")) { // SpEL expressions
            throw new SecurityException(
                "Variable '" + variableName + "' value contains suspicious expression patterns"
            );
        }
    }
    
    /**
     * Checks if a variable type is safe to use.
     * 
     * @param type the class type to validate
     * @return true if the type is safe, false otherwise
     */
    public boolean isSafeType(Class<?> type) {
        if (type == null) {
            return false;
        }
        
        // Primitive types and wrappers are safe
        if (type.isPrimitive() || 
            type == String.class ||
            type == Integer.class ||
            type == Long.class ||
            type == Double.class ||
            type == Float.class ||
            type == Boolean.class ||
            type == Character.class ||
            type == Byte.class ||
            type == Short.class) {
            return true;
        }
        
        // Dangerous types
        if (type == Class.class ||
            type == ClassLoader.class ||
            type == Runtime.class ||
            type == Process.class ||
            type == ProcessBuilder.class) {
            return false;
        }
        
        // Package-based checks
        String packageName = type.getPackage() != null ? type.getPackage().getName() : "";
        
        // Reflection packages are dangerous
        if (packageName.startsWith("java.lang.reflect") ||
            packageName.startsWith("java.lang.invoke")) {
            return false;
        }
        
        // Default: allow
        return true;
    }
    
    /**
     * Adds a custom forbidden keyword.
     * 
     * <p>This allows applications to extend the security policy with domain-specific keywords.
     * 
     * @param keyword the keyword to forbid (case-insensitive)
     */
    public void addForbiddenKeyword(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            FORBIDDEN_KEYWORDS.add(keyword.toLowerCase().trim());
        }
    }
    
    /**
     * Gets the set of forbidden keywords (unmodifiable view).
     * 
     * @return the forbidden keywords
     */
    public Set<String> getForbiddenKeywords() {
        return new HashSet<>(FORBIDDEN_KEYWORDS);
    }
}
