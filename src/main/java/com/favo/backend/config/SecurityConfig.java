package com.favo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()  // PUBLIC
                        .anyRequest().authenticated()                // PRIVATE
                )
                .formLogin(form -> form.disable())                  // Disable default login page
                .httpBasic(Customizer.withDefaults());              // New required syntax

        return http.build();
    }
}
