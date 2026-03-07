package com.travelapp.review.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenValidationFilter tokenValidationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // swagger / actuator
                        .requestMatchers(
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()

                        // internal
                        .requestMatchers("/internal/**").permitAll()

                        // public review read endpoints
                        .requestMatchers(HttpMethod.GET,
                                "/v1/reviews/poi/**",
                                "/v1/reviews/user/**",
                                "/v1/reviews/poi/*/user/*",
                                "/v1/reviews/poi/*/stats"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/v1/reviews/*").permitAll()

                        // authenticated review endpoints
                        .requestMatchers(HttpMethod.POST, "/v1/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/reviews/*/like").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/reviews/check/*").authenticated()

                        // review moderation
                        .requestMatchers(HttpMethod.POST, "/v1/reviews/*/hide").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/v1/reviews/*/unhide").hasAnyRole("ADMIN", "MODERATOR")

                        // user report endpoints
                        .requestMatchers(HttpMethod.POST, "/v1/reports").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/reports/my").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/reports/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/reports/*").authenticated()

                        // moderator/admin report endpoints
                        .requestMatchers(HttpMethod.GET, "/v1/reports").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.GET, "/v1/reports/status/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.GET, "/v1/reports/moderator/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.GET, "/v1/reports/stats/pending-count").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/v1/reports/*/process").hasAnyRole("ADMIN", "MODERATOR")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenValidationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}