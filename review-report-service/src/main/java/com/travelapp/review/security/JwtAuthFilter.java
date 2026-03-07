package com.travelapp.review.security;

import com.travelapp.review.client.AuthClient;
import com.travelapp.review.exception.UnauthorizedException;
import com.travelapp.review.model.dto.InternalUserResponse;
import com.travelapp.review.security.AuthPrincipal;
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

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank() || !authHeader.trim().startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            InternalUserResponse me = authClient.getCurrentUser(authHeader);

            if (Boolean.TRUE.equals(me.getIsBlocked())) {
                throw new UnauthorizedException("User is blocked");
            }

            String role = me.getRole() == null ? "USER" : me.getRole().trim().toUpperCase();
            if (role.equals("ADMIN")) {
                role = "ROLE_ADMIN";
            } else if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }

            AuthPrincipal principal = new AuthPrincipal(
                    me.getId(),
                    me.getRole(),
                    me.getStatus(),
                    me.getIsBlocked()
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority(role))
                    );

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (UnauthorizedException e) {
            log.debug("Unauthorized token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.warn("Auth filter failed: {}", e.toString());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}