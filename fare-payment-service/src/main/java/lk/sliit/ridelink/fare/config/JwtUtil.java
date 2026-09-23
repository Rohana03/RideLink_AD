package lk.sliit.ridelink.fare.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Verifies JWTs issued by the Account Service using the shared JWT_SECRET.
 * This service never issues tokens and never calls the Account Service to check one.
 */
@Component
@Slf4j
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtUtil(@Value("${ridelink.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "ridelink.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdClaim = claims.get("userId");
        return userIdClaim != null ? String.valueOf(userIdClaim) : claims.getSubject();
    }

    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> roles) {
            return roles.stream().map(Object::toString).toList();
        }
        Object roleObj = claims.get("role");
        return roleObj != null ? List.of(roleObj.toString()) : Collections.emptyList();
    }

    /** Signature and expiry check; jjwt rejects expired tokens while parsing. */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}
