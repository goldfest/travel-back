package com.travelapp.poi.security;

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
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth

                        // swagger / actuator
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**"
                        ).permitAll()

                        // -------- PUBLIC READ --------

                        .requestMatchers(HttpMethod.GET,
                                "/pois/**",
                                "/poi-types/**",
                                "/import/tasks/**",
                                "/import/tasks",
                                "/import/tasks/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/pois/search").permitAll()

                        // -------- ADMIN ONLY --------

                        .requestMatchers(HttpMethod.POST, "/import/start").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/import/tasks/*/cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/import/tasks/*/retry").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/pois/*/verify").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/pois/*/unverify").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/pois/unverified").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/poi-types").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/poi-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/poi-types/**").hasRole("ADMIN")

                        // -------- AUTHENTICATED --------

                        .requestMatchers(HttpMethod.POST, "/pois").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/pois/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/pois/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}