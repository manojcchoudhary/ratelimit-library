package com.lycosoft.ratelimit.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VariableValidator security enforcement.
 */
class VariableValidatorTest {
    
    private VariableValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new VariableValidator();
    }
    
    @Test
    void shouldAcceptValidVariableNames() {
        // Valid names
        assertDoesNotThrow(() -> validator.validateVariableName("user"));
        assertDoesNotThrow(() -> validator.validateVariableName("userId"));
        assertDoesNotThrow(() -> validator.validateVariableName("user_id"));
        assertDoesNotThrow(() -> validator.validateVariableName("userName123"));
        assertDoesNotThrow(() -> validator.validateVariableName("API_KEY_ID"));
    }
    
    @Test
    void shouldRejectNullOrEmptyNames() {
        assertThrows(SecurityException.class, () -> validator.validateVariableName(null));
        assertThrows(SecurityException.class, () -> validator.validateVariableName(""));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("   "));
    }
    
    @Test
    void shouldRejectForbiddenKeywords() {
        // Class-related
        assertThrows(SecurityException.class, () -> validator.validateVariableName("class"));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("forName"));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("classLoader"));
        
        // Runtime-related
        assertThrows(SecurityException.class, () -> validator.validateVariableName("runtime"));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("system"));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("process"));
        
        // Reflection
        assertThrows(SecurityException.class, () -> validator.validateVariableName("method"));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("constructor"));
        assertThrows(SecurityException.class, () -> validator.validateVariableName("invoke"));
    }
    
    @Test
    void shouldRejectInjectionCharacters() {
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user()")); // Parentheses
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user{}")); // Braces
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user[]")); // Brackets
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user.name")); // Dot
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user$name")); // Dollar
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user#name")); // Hash
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user;drop")); // Semicolon
    }
    
    @Test
    void shouldRejectInvalidFormats() {
        assertThrows(SecurityException.class, () -> validator.validateVariableName("123user")); // Starts with digit
        assertThrows(SecurityException.class, () -> validator.validateVariableName("_user")); // Starts with underscore
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user-name")); // Hyphen not allowed
        assertThrows(SecurityException.class, () -> validator.validateVariableName("user name")); // Space not allowed
    }
    
    @Test
    void shouldDetectForbiddenKeywordsInValues() {
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "java.lang.Runtime.getRuntime()"));
        
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "Class.forName('Malicious')"));
        
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "System.exit(0)"));
    }
    
    @Test
    void shouldDetectSpELInjectionPatterns() {
        // T() expression
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "T(java.lang.Runtime)"));
        
        // Bean references
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "@beanName"));
        
        // Property placeholders
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "${malicious.property}"));
        
        // SpEL expressions
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableValue("myVar", "#{maliciousExpression}"));
    }
    
    @Test
    void shouldAcceptSafeValues() {
        assertDoesNotThrow(() -> validator.validateVariableValue("user", "john.doe"));
        assertDoesNotThrow(() -> validator.validateVariableValue("count", 42));
        assertDoesNotThrow(() -> validator.validateVariableValue("flag", true));
        assertDoesNotThrow(() -> validator.validateVariableValue("nullable", null));
    }
    
    @Test
    void shouldIdentifySafeTypes() {
        // Primitive types
        assertTrue(validator.isSafeType(int.class));
        assertTrue(validator.isSafeType(long.class));
        assertTrue(validator.isSafeType(boolean.class));
        
        // Wrapper types
        assertTrue(validator.isSafeType(Integer.class));
        assertTrue(validator.isSafeType(String.class));
        assertTrue(validator.isSafeType(Boolean.class));
    }
    
    @Test
    void shouldIdentifyDangerousTypes() {
        assertFalse(validator.isSafeType(Class.class));
        assertFalse(validator.isSafeType(ClassLoader.class));
        assertFalse(validator.isSafeType(Runtime.class));
        assertFalse(validator.isSafeType(Process.class));
        assertFalse(validator.isSafeType(ProcessBuilder.class));
    }
    
    @Test
    void shouldAllowAddingCustomForbiddenKeywords() {
        validator.addForbiddenKeyword("custom");
        
        assertThrows(SecurityException.class, () -> 
            validator.validateVariableName("customVariable"));
    }
    
    @Test
    void shouldReturnForbiddenKeywords() {
        var keywords = validator.getForbiddenKeywords();
        
        assertThat(keywords).contains("class", "runtime", "system", "process");
        assertThat(keywords.size()).isGreaterThan(10);
    }
}
