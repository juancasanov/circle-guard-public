package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String SECRET = "my-super-secret-dev-key-32-chars-long-12345678";
    private static final long EXPIRATION_MS = 3600000;

    private JwtTokenService jwtTokenService;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION_MS);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    void generateToken_shouldProduceValidJwtContainingSubjectAndPermissions() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("auth:login"),
                new SimpleGrantedAuthority("auth:qr")
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(
                anonymousId.toString(), null, authorities);

        // Act
        String token = jwtTokenService.generateToken(anonymousId, auth);

        // Assert
        assertNotNull(token);
        assertFalse(token.isBlank());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions", List.class);
        assertNotNull(permissions);
        assertTrue(permissions.contains("ROLE_USER"));
        assertTrue(permissions.contains("auth:login"));
        assertTrue(permissions.contains("auth:qr"));
    }

    @Test
    void generateToken_shouldHandleAuthenticationWithNoAuthorities() {
        // Arrange
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                anonymousId.toString(), null, List.of());

        // Act
        String token = jwtTokenService.generateToken(anonymousId, auth);

        // Assert
        assertNotNull(token);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions", List.class);
        assertNotNull(permissions);
        assertTrue(permissions.isEmpty());
    }
}
