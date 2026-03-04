package com.travelapp.review.config;

import com.travelapp.review.client.AuthClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TokenValidationFilter extends OncePerRequestFilter {

    private final AuthClient authClient;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Пока просто пропускаем всё (чтобы сервис стартовал).
        // Позже добавим реальную проверку токена через authClient.
        filterChain.doFilter(request, response);
    }
}