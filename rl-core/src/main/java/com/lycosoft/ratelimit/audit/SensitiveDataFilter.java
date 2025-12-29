package com.lycosoft.ratelimit.audit;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Filters sensitive data from configuration objects before logging.
 * 
 * <p>This filter automatically masks sensitive values (passwords, tokens, API keys)
 * to prevent credential leakage in audit logs.
 * 
 * <p><b>Default Patterns:</b> password, secret, token, key, credential, apikey, auth
 * 
 * @since 1.0.0
 */
public class SensitiveDataFilter {
    
    private static final String MASK = "********";
    
    /**
     * Default patterns for sensitive keys (case-insensitive).
     */
    private static final List<Pattern> DEFAULT_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*password.*"),
        Pattern.compile("(?i).*secret.*"),
        Pattern.compile("(?i).*token.*"),
        Pattern.compile("(?i).*key.*"),
        Pattern.compile("(?i).*credential.*"),
        Pattern.compile("(?i).*apikey.*"),
        Pattern.compile("(?i).*auth.*")
    );
    
    private final List<Pattern> sensitivePatterns;
    
    /**
     * Creates a filter with default sensitive patterns.
     */
    public SensitiveDataFilter() {
        this.sensitivePatterns = new ArrayList<>(DEFAULT_PATTERNS);
    }
    
    /**
     * Creates a filter with custom sensitive patterns.
     * 
     * @param patterns custom regex patterns for sensitive keys
     */
    public SensitiveDataFilter(List<String> patterns) {
        this.sensitivePatterns = new ArrayList<>();
        for (String pattern : patterns) {
            this.sensitivePatterns.add(Pattern.compile(pattern));
        }
    }
    
    /**
     * Filters a configuration map, masking sensitive values.
     * 
     * <p>This method creates a new map and recursively filters nested structures.
     * 
     * @param config the configuration map to filter
     * @return a new map with sensitive values masked
     */
    public Map<String, Object> filterSensitiveData(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        
        Map<String, Object> filtered = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (isSensitiveKey(key)) {
                // Mask sensitive value
                filtered.put(key, MASK);
            } else if (value instanceof Map) {
                // Recursively filter nested maps
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                filtered.put(key, filterSensitiveData(nestedMap));
            } else if (value instanceof List) {
                // Filter lists (in case they contain maps)
                filtered.put(key, filterList((List<?>) value));
            } else {
                // Copy non-sensitive value as-is
                filtered.put(key, value);
            }
        }
        
        return filtered;
    }
    
    /**
     * Filters a list, recursively filtering any nested maps.
     * 
     * @param list the list to filter
     * @return a new list with sensitive values in nested maps masked
     */
    private List<?> filterList(List<?> list) {
        if (list == null) {
            return null;
        }
        
        List<Object> filtered = new ArrayList<>();
        
        for (Object item : list) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                filtered.add(filterSensitiveData(map));
            } else if (item instanceof List) {
                filtered.add(filterList((List<?>) item));
            } else {
                filtered.add(item);
            }
        }
        
        return filtered;
    }
    
    /**
     * Checks if a key matches any sensitive pattern.
     * 
     * @param key the key to check
     * @return true if the key is sensitive, false otherwise
     */
    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        
        for (Pattern pattern : sensitivePatterns) {
            if (pattern.matcher(key).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Adds a custom sensitive pattern.
     * 
     * @param pattern the regex pattern to add
     */
    public void addSensitivePattern(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            this.sensitivePatterns.add(Pattern.compile(pattern));
        }
    }
    
    /**
     * Gets the current sensitive patterns.
     * 
     * @return a copy of the sensitive patterns
     */
    public List<String> getSensitivePatterns() {
        List<String> patterns = new ArrayList<>();
        for (Pattern pattern : sensitivePatterns) {
            patterns.add(pattern.pattern());
        }
        return patterns;
    }
    
    /**
     * Masks a single value if it appears to contain sensitive data.
     * 
     * @param value the value to check and potentially mask
     * @return the original value if safe, or masked value if sensitive
     */
    public String maskIfSensitive(String value) {
        if (value == null) {
            return null;
        }
        
        String lowerValue = value.toLowerCase();
        
        // Check if value contains sensitive keywords
        if (lowerValue.contains("password") ||
            lowerValue.contains("secret") ||
            lowerValue.contains("token") ||
            lowerValue.contains("key=") ||
            lowerValue.contains("apikey")) {
            return MASK;
        }
        
        return value;
    }
}
