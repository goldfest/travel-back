package com.travelapp.review.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenValidator {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    public JwtClaims validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Проверяем expiration
            if (claims.getExpiration().before(new Date())) {
                return null;
            }

            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setUserId(claims.get("userId", Long.class));
            jwtClaims.setUsername(claims.getSubject());
            jwtClaims.setRole(claims.get("role", String.class));

            return jwtClaims;

        } catch (Exception e) {
            return null;
        }
    }

    @Getter
    @Setter
    public static class JwtClaims {
        private Long userId;
        private String username;
        private String role;
    }
}