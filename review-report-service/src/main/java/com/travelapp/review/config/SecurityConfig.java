@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthClient authClient;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/reviews/poi/**",
                                "/api/v1/reviews/{id}",
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/actuator/health"
                        ).permitAll()

                        // User endpoints
                        .requestMatchers(
                                "/api/v1/reviews/**",
                                "/api/v1/reports/my/**"
                        ).authenticated()

                        // Admin/Moderator endpoints
                        .requestMatchers(
                                "/api/v1/reviews/*/hide",
                                "/api/v1/reviews/*/unhide",
                                "/api/v1/reports/**"
                        ).hasAnyRole("ADMIN", "MODERATOR")

                        // Internal endpoints for other services
                        .requestMatchers("/internal/**").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(new TokenValidationFilter(authClient),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}