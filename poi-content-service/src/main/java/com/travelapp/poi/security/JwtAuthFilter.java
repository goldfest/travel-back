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

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || header.isBlank() || !header.trim().startsWith("Bearer ")) {
            log.debug("No Bearer token for {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            log.debug("Validating token via auth-service for {} {}", request.getMethod(), request.getRequestURI());

            InternalUserResponse me = authClient.getCurrentUser(header);

            if (Boolean.TRUE.equals(me.getIsBlocked())) {
                throw new UnauthorizedException("User is blocked");
            }

            String role = me.getRole() == null ? "USER" : me.getRole().trim().toUpperCase();
            if (role.equals("ADMIN")) role = "ROLE_ADMIN";
            else if (!role.startsWith("ROLE_")) role = "ROLE_" + role;

            AuthPrincipal principal = new AuthPrincipal(
                    me.getId(),
                    me.getRole(),
                    me.getStatus(),
                    me.getIsBlocked()
            );

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(role))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.info("Authenticated user id={}, role={} for {} {}", me.getId(), role, request.getMethod(), request.getRequestURI());

        } catch (UnauthorizedException e) {
            log.warn("Unauthorized token for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.error("Auth filter failed for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}