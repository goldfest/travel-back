package com.travelapp.poi.security;

import com.travelapp.poi.exception.UnauthorizedException;
import com.travelapp.poi.client.AuthClient;
import com.travelapp.poi.model.dto.InternalUserResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthClient authClient;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Если уже аутентифицирован — ничего не делаем
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank() || !header.trim().startsWith("Bearer ")) {
            // токена нет — просто идём дальше (доступ решит SecurityConfig)
            filterChain.doFilter(request, response);
            return;
        }

        try {
            InternalUserResponse me = authClient.getCurrentUser(header);

            // если заблокирован — считаем неавторизованным
            if (Boolean.TRUE.equals(me.getIsBlocked())) {
                throw new UnauthorizedException("User is blocked");
            }

            String role = me.getRole() == null ? "USER" : me.getRole().trim().toUpperCase();
            if (role.equals("ADMIN")) role = "ROLE_ADMIN";
            else if (!role.startsWith("ROLE_")) role = "ROLE_" + role;

            AuthPrincipal principal = new AuthPrincipal(me.getId(), me.getRole(), me.getStatus(), me.getIsBlocked());

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(role))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (UnauthorizedException e) {
            // токен плохой/просрочен — просто не ставим auth и идём дальше;
            // если эндпоинт требует auth, дальше сработает entry point (401)
            log.debug("Unauthorized token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            // fail-closed: при проблемах с auth-service лучше НЕ давать доступ
            log.warn("Auth filter failed: {}", e.toString());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}