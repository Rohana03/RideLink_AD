package lk.sliit.ridelink.account.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lk.sliit.ridelink.account.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies the RideLink JWT. This service is the only issuer; the other
 * services verify the same token locally with the shared JWT_SECRET.
 *
 * <p>Token contract (must stay in sync with every service):
 * <ul>
 *   <li>HS256, signed with JWT_SECRET (at least 32 bytes)</li>
 *   <li>{@code sub} and {@code userId}: the account ID</li>
 *   <li>{@code roles}: JSON array such as ["DRIVER"], without a "ROLE_" prefix</li>
 *   <li>{@code email}, {@code iss}, {@code iat}, {@code exp}</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtUtil {

    public static final String ISSUER = "ridelink-account-service";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${ridelink.jwt.secret}") String secret,
            @Value("${ridelink.jwt.expiration-ms:3600000}") long expirationMs
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Fail fast instead of padding: a short secret would be weak and easy to mistype across services.
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "ridelink.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(Account account) {
        Date now = new Date();
        return Jwts.builder()
                .subject(account.getId())
                .claim("userId", account.getId())
                .claim("roles", List.of(account.getRole().name()))
                .claim("email", account.getEmail())
                .issuer(ISSUER)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
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
