package com.travelapp.city.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/actuator/**").permitAll()

                        // Public read (справочник городов)
                        .requestMatchers(HttpMethod.GET, "/v1/**").permitAll()

                        // Endpoint "мой домашний город" требует авторизацию
                        .requestMatchers(HttpMethod.GET, "/v1/users/me/**").authenticated()

                        // Любые изменения справочника — только ADMIN
                        .requestMatchers(HttpMethod.POST, "/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}