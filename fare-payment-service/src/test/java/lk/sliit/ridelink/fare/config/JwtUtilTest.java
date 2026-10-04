package lk.sliit.ridelink.fare.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test-only-jwt-secret-key-not-used-outside-unit-tests-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    /** Builds a token the way the Account Service does. */
    private static String accountServiceToken(String secret, long ttlMs) {
        return Jwts.builder()
                .subject("acc-psg-001")
                .claim("userId", "acc-psg-001")
                .claim("roles", List.of("PASSENGER"))
                .issuer("ridelink-account-service")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    @DisplayName("Accepts an Account Service token and reads userId and roles")
    void readsAccountServiceToken() {
        String token = accountServiceToken(SECRET, 60_000);

        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals("acc-psg-001", jwtUtil.extractUserId(token));
        assertEquals(List.of("PASSENGER"), jwtUtil.extractRoles(token));
    }

    @Test
    @DisplayName("Rejects a token signed with another secret")
    void rejectsForeignToken() {
        assertFalse(jwtUtil.isTokenValid(accountServiceToken("another-secret-that-is-also-long-enough-0123456789", 60_000)));
    }

    @Test
    @DisplayName("Rejects an expired token")
    void rejectsExpiredToken() {
        assertFalse(jwtUtil.isTokenValid(accountServiceToken(SECRET, -1_000)));
    }

    @Test
    @DisplayName("Rejects garbage without throwing")
    void rejectsGarbage() {
        assertFalse(jwtUtil.isTokenValid("not-a-jwt"));
    }

    @Test
    @DisplayName("A secret shorter than 32 bytes fails fast at start-up")
    void shortSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short-secret"));
    }
}
