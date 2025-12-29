package com.lycosoft.ratelimit.example.springboot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for tiered user authentication.
 * 
 * <p>Creates two user tiers for demonstrating user-based rate limiting:
 * <ul>
 *   <li><b>BASIC</b> tier: Limited API quota (100 req/hour)</li>
 *   <li><b>PREMIUM</b> tier: Higher API quota (1000 req/hour)</li>
 * </ul>
 * 
 * <p><b>Test Users:</b>
 * <pre>
 * Username: basic    | Password: password | Role: BASIC
 * Username: premium  | Password: password | Role: PREMIUM
 * </pre>
 * 
 * <p><b>Usage:</b>
 * <pre>
 * # BASIC user
 * curl -u basic:password http://localhost:8080/api/user/profile
 * 
 * # PREMIUM user
 * curl -u premium:password http://localhost:8080/api/user/dashboard
 * </pre>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * Configure HTTP security.
     * 
     * <p>Allows:
     * <ul>
     *   <li>Public API endpoints (no auth required)</li>
     *   <li>Partner API endpoints (no auth, uses header-based rate limiting)</li>
     *   <li>Actuator endpoints (for monitoring)</li>
     * </ul>
     * 
     * <p>Requires auth for:
     * <ul>
     *   <li>User API endpoints (HTTP Basic Auth)</li>
     * </ul>
     * 
     * @param http the HTTP security configuration
     * @return the security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Disable CSRF for API
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()      // Public endpoints
                .requestMatchers("/api/partner/**").permitAll()     // Partner endpoints
                .requestMatchers("/actuator/**").permitAll()        // Actuator endpoints
                .requestMatchers("/api/user/**").authenticated()    // User endpoints require auth
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});  // Enable HTTP Basic Auth
        
        return http.build();
    }
    
    /**
     * Create in-memory user details service with tiered users.
     * 
     * <p>Creates two users with different roles to demonstrate
     * tier-based rate limiting.
     * 
     * @param passwordEncoder the password encoder
     * @return user details service with test users
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // BASIC tier user (100 req/hour limit)
        UserDetails basicUser = User.builder()
            .username("basic")
            .password(passwordEncoder.encode("password"))
            .roles("BASIC")
            .build();
        
        // PREMIUM tier user (1000 req/hour limit)
        UserDetails premiumUser = User.builder()
            .username("premium")
            .password(passwordEncoder.encode("password"))
            .roles("PREMIUM")
            .build();
        
        return new InMemoryUserDetailsManager(basicUser, premiumUser);
    }
    
    /**
     * Password encoder bean.
     * 
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
