package com.lycosoft.ratelimit.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
     * Default forbidden keywords that indicate potential security risks.
     * These keywords are commonly used in injection attacks.
     * This set is immutable - custom keywords are stored in instance field.
     */
    private static final Set<String> DEFAULT_FORBIDDEN_KEYWORDS = Set.of(
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
        "scriptengine", "scriptenginemanager",

        // Additional dangerous patterns (added for completeness)
        "introspector", "beaninfo", "propertyeditor",  // java.beans abuse
        "nashorn", "rhino", "javascript",  // Script engine names
        "exec", "shell", "cmd",  // Command execution
        "getenv", "setproperty"  // Environment access
    );

    /**
     * Thread-safe set for custom forbidden keywords added at runtime.
     * Uses ConcurrentHashMap.newKeySet() for thread-safe operations.
     */
    private final Set<String> customForbiddenKeywords = ConcurrentHashMap.newKeySet();
    
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

        // Check for forbidden keywords (both default and custom)
        checkForbiddenKeywords(normalized, variableName);

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
     * Checks if a normalized string contains any forbidden keywords.
     *
     * @param normalized the lowercase normalized string to check
     * @param originalValue the original value for error messages
     * @throws SecurityException if a forbidden keyword is found
     */
    private void checkForbiddenKeywords(String normalized, String originalValue) {
        // Check default forbidden keywords
        for (String keyword : DEFAULT_FORBIDDEN_KEYWORDS) {
            if (normalized.contains(keyword)) {
                throw new SecurityException(
                    "Value '" + originalValue + "' contains forbidden keyword: " + keyword
                );
            }
        }

        // Check custom forbidden keywords (thread-safe iteration)
        for (String keyword : customForbiddenKeywords) {
            if (normalized.contains(keyword)) {
                throw new SecurityException(
                    "Value '" + originalValue + "' contains forbidden keyword: " + keyword
                );
            }
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

        String originalValue = value.toString();
        String valueStr = originalValue.toLowerCase();

        // Check for forbidden keywords in value (both default and custom)
        checkForbiddenKeywords(valueStr, "Variable '" + variableName + "' value");

        // Check for suspicious patterns that might indicate code injection
        // SECURITY FIX: Check both lowercase and original case for T() expression
        // SpEL uses T(classname) for type references and is case-sensitive
        if (valueStr.contains("t(") ||            // lowercase t()
            originalValue.contains("T(") ||        // FIXED: uppercase T() - SpEL type expression
            valueStr.contains("@") ||              // Bean references in SpEL
            valueStr.contains("${") ||             // Property placeholders
            valueStr.contains("#{") ||             // SpEL expressions
            originalValue.contains("new ") ||      // Object instantiation
            valueStr.contains("getclass") ||       // getClass() method call
            valueStr.contains("getruntime")) {     // Runtime.getRuntime()
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

        // Dangerous types - explicit class checks
        if (type == Class.class ||
            type == ClassLoader.class ||
            type == Runtime.class ||
            type == Process.class ||
            type == ProcessBuilder.class) {
            return false;
        }

        // Package-based checks for dangerous packages
        String packageName = type.getPackage() != null ? type.getPackage().getName() : "";
        String className = type.getName();

        // Reflection packages are dangerous
        if (packageName.startsWith("java.lang.reflect") ||
            packageName.startsWith("java.lang.invoke")) {
            return false;
        }

        // SECURITY FIX: Additional dangerous packages that were missing
        if (packageName.startsWith("sun.reflect") ||           // Sun reflection internals
            packageName.startsWith("sun.misc") ||              // Unsafe, etc.
            packageName.startsWith("jdk.internal") ||          // JDK internals
            packageName.startsWith("java.beans") ||            // Introspector, PropertyEditor
            packageName.startsWith("javax.script") ||          // ScriptEngine
            packageName.startsWith("javax.management") ||      // JMX (can execute code)
            packageName.startsWith("java.rmi") ||              // RMI (remote code execution)
            packageName.startsWith("javax.naming") ||          // JNDI (JNDI injection)
            packageName.startsWith("java.lang.instrument")) {  // Instrumentation
            return false;
        }

        // Check for specific dangerous classes by name pattern
        if (className.contains("Unsafe") ||
            className.contains("ScriptEngine") ||
            className.contains("Introspector") ||
            className.contains("MethodHandle")) {
            return false;
        }

        // Default: allow
        return true;
    }
    
    /**
     * Adds a custom forbidden keyword.
     *
     * <p>This allows applications to extend the security policy with domain-specific keywords.
     * Custom keywords are stored in an instance-level thread-safe set, separate from the
     * immutable default keywords. This ensures thread-safety and prevents global state mutation.
     *
     * @param keyword the keyword to forbid (case-insensitive)
     */
    public void addForbiddenKeyword(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            // Thread-safe addition to instance-level custom keywords set
            customForbiddenKeywords.add(keyword.toLowerCase().trim());
        }
    }

    /**
     * Gets the combined set of forbidden keywords (default + custom).
     *
     * @return an unmodifiable view of all forbidden keywords
     */
    public Set<String> getForbiddenKeywords() {
        // Combine default and custom keywords into a new set
        Set<String> combined = ConcurrentHashMap.newKeySet();
        combined.addAll(DEFAULT_FORBIDDEN_KEYWORDS);
        combined.addAll(customForbiddenKeywords);
        return Collections.unmodifiableSet(combined);
    }

    /**
     * Clears all custom forbidden keywords.
     *
     * <p>This does not affect the default forbidden keywords.
     */
    public void clearCustomKeywords() {
        customForbiddenKeywords.clear();
    }
}
