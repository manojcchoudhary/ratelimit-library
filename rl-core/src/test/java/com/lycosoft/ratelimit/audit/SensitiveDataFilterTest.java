package com.lycosoft.ratelimit.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SensitiveDataFilter.
 */
class SensitiveDataFilterTest {
    
    private SensitiveDataFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new SensitiveDataFilter();
    }
    
    @Test
    void shouldMaskPasswordFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("username", "admin");
        config.put("password", "secret123");
        config.put("database", "mydb");
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered.get("username")).isEqualTo("admin");
        assertThat(filtered.get("password")).isEqualTo("********");
        assertThat(filtered.get("database")).isEqualTo("mydb");
    }
    
    @Test
    void shouldMaskSecretFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("api_key", "abc123");
        config.put("api_secret", "xyz789");
        config.put("timeout", 30);
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered.get("api_key")).isEqualTo("********");
        assertThat(filtered.get("api_secret")).isEqualTo("********");
        assertThat(filtered.get("timeout")).isEqualTo(30);
    }
    
    @Test
    void shouldMaskTokenFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("access_token", "bearer_token_12345");
        config.put("refresh_token", "refresh_67890");
        config.put("expires_in", 3600);
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered.get("access_token")).isEqualTo("********");
        assertThat(filtered.get("refresh_token")).isEqualTo("********");
        assertThat(filtered.get("expires_in")).isEqualTo(3600);
    }
    
    @Test
    void shouldHandleNestedMaps() {
        Map<String, Object> redis = new HashMap<>();
        redis.put("host", "localhost");
        redis.put("password", "redis_secret");
        
        Map<String, Object> config = new HashMap<>();
        config.put("redis", redis);
        config.put("timeout", 5000);
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> filteredRedis = (Map<String, Object>) filtered.get("redis");
        
        assertThat(filteredRedis.get("host")).isEqualTo("localhost");
        assertThat(filteredRedis.get("password")).isEqualTo("********");
        assertThat(filtered.get("timeout")).isEqualTo(5000);
    }
    
    @Test
    void shouldHandleListsOfMaps() {
        Map<String, Object> server1 = new HashMap<>();
        server1.put("name", "server1");
        server1.put("api_key", "key1");
        
        Map<String, Object> server2 = new HashMap<>();
        server2.put("name", "server2");
        server2.put("api_key", "key2");
        
        Map<String, Object> config = new HashMap<>();
        config.put("servers", Arrays.asList(server1, server2));
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) filtered.get("servers");
        
        assertThat(servers).hasSize(2);
        assertThat(servers.get(0).get("name")).isEqualTo("server1");
        assertThat(servers.get(0).get("api_key")).isEqualTo("********");
        assertThat(servers.get(1).get("name")).isEqualTo("server2");
        assertThat(servers.get(1).get("api_key")).isEqualTo("********");
    }
    
    @Test
    void shouldMaskVariationsOfSensitiveKeys() {
        Map<String, Object> config = new HashMap<>();
        config.put("PASSWORD", "secret1");
        config.put("db_password", "secret2");
        config.put("password_hash", "secret3");
        config.put("my_secret_key", "secret4");
        config.put("oauth_token", "secret5");
        config.put("normal_field", "normal_value");
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered.get("PASSWORD")).isEqualTo("********");
        assertThat(filtered.get("db_password")).isEqualTo("********");
        assertThat(filtered.get("password_hash")).isEqualTo("********");
        assertThat(filtered.get("my_secret_key")).isEqualTo("********");
        assertThat(filtered.get("oauth_token")).isEqualTo("********");
        assertThat(filtered.get("normal_field")).isEqualTo("normal_value");
    }
    
    @Test
    void shouldHandleNullInput() {
        Map<String, Object> filtered = filter.filterSensitiveData(null);
        assertThat(filtered).isNull();
    }
    
    @Test
    void shouldHandleEmptyMap() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered).isEmpty();
    }
    
    @Test
    void shouldAllowCustomPatterns() {
        List<String> customPatterns = Arrays.asList(
            "(?i).*ssn.*",
            "(?i).*creditcard.*"
        );
        
        filter = new SensitiveDataFilter(customPatterns);
        
        Map<String, Object> config = new HashMap<>();
        config.put("user_ssn", "123-45-6789");
        config.put("creditcard_number", "4111111111111111");
        config.put("name", "John Doe");
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered.get("user_ssn")).isEqualTo("********");
        assertThat(filtered.get("creditcard_number")).isEqualTo("********");
        assertThat(filtered.get("name")).isEqualTo("John Doe");
    }
    
    @Test
    void shouldMaskSingleSensitiveValue() {
        assertThat(filter.maskIfSensitive("normal value")).isEqualTo("normal value");
        assertThat(filter.maskIfSensitive("password=secret")).isEqualTo("********");
        assertThat(filter.maskIfSensitive("token:abc123")).isEqualTo("********");
        assertThat(filter.maskIfSensitive(null)).isNull();
    }
    
    @Test
    void shouldAddSensitivePattern() {
        filter.addSensitivePattern("(?i).*pin.*");
        
        Map<String, Object> config = new HashMap<>();
        config.put("user_pin", "1234");
        config.put("name", "John");
        
        Map<String, Object> filtered = filter.filterSensitiveData(config);
        
        assertThat(filtered.get("user_pin")).isEqualTo("********");
        assertThat(filtered.get("name")).isEqualTo("John");
    }
}
