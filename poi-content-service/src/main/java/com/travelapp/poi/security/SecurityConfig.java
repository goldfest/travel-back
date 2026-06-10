package com.travelapp.poi.security;

import jakarta.servlet.http.HttpServletResponse;
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

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.setStatus(HttpServletResponse.SC_FORBIDDEN))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/poi/**").permitAll()

                        // Media moderation must be checked before public GET /pois/** rule
                        .requestMatchers(HttpMethod.GET, "/pois/media/pending").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/pois/*/media/admin").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/pois/*/media/*/approve").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/pois/*/media/*/reject").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/pois/*/media/*").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/pois/*/media").authenticated()

                        .requestMatchers(HttpMethod.GET,
                                "/pois/**",
                                "/poi-types/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/pois/search").permitAll()

                        .requestMatchers(HttpMethod.POST, "/pois").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.PUT, "/pois/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/pois/**").hasAnyRole("ADMIN", "MODERATOR")

                        .requestMatchers(HttpMethod.GET, "/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/internal/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/internal/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/poi-types").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.PUT, "/poi-types/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/poi-types/**").hasAnyRole("ADMIN", "MODERATOR")

                        .requestMatchers("/import/**").hasAnyRole("ADMIN", "MODERATOR")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
