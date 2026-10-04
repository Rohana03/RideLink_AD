package lk.sliit.ridelink.account.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lk.sliit.ridelink.account.entity.Account;
import lk.sliit.ridelink.account.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test-only-jwt-secret-key-not-used-outside-unit-tests-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000);

    private Account driverAccount() {
        return Account.builder()
                .id("acc-drv-001")
                .email("nimal@example.com")
                .role(Role.DRIVER)
                .build();
    }

    @Test
    @DisplayName("Token follows the shared contract: userId, sub, roles array, issuer, exp")
    void tokenMatchesSharedContract() {
        String token = jwtUtil.generateToken(driverAccount());

        // Parse independently with the raw secret, exactly as the other services do.
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("acc-drv-001", claims.get("userId"));
        assertEquals("acc-drv-001", claims.getSubject());
        assertEquals(List.of("DRIVER"), claims.get("roles"));
        assertEquals("nimal@example.com", claims.get("email"));
        assertEquals(JwtUtil.ISSUER, claims.getIssuer());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    @DisplayName("Round trip: a generated token is valid and yields the same userId and roles")
    void generatedTokenRoundTrips() {
        String token = jwtUtil.generateToken(driverAccount());

        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals("acc-drv-001", jwtUtil.extractUserId(token));
        assertEquals(List.of("DRIVER"), jwtUtil.extractRoles(token));
        assertEquals(3600, jwtUtil.getExpirationSeconds());
    }

    @Test
    @DisplayName("A token with a tampered payload is rejected")
    void tamperedTokenIsInvalid() {
        String token = jwtUtil.generateToken(driverAccount());
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "xx." + parts[2];

        assertFalse(jwtUtil.isTokenValid(forged));
    }

    @Test
    @DisplayName("A token signed with a different secret is rejected")
    void tokenFromOtherSecretIsInvalid() {
        JwtUtil other = new JwtUtil("another-secret-that-is-also-long-enough-0123456789", 3_600_000);
        String token = other.generateToken(driverAccount());

        assertFalse(jwtUtil.isTokenValid(token));
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredTokenIsInvalid() {
        JwtUtil shortLived = new JwtUtil(SECRET, -1_000);
        String token = shortLived.generateToken(driverAccount());

        assertFalse(jwtUtil.isTokenValid(token));
    }

    @Test
    @DisplayName("Garbage and empty tokens are rejected without throwing")
    void garbageTokenIsInvalid() {
        assertFalse(jwtUtil.isTokenValid("not-a-jwt"));
        assertFalse(jwtUtil.isTokenValid(""));
    }

    @Test
    @DisplayName("A secret shorter than 32 bytes fails fast at start-up")
    void shortSecretIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtUtil("too-short-secret", 3_600_000));
        assertTrue(ex.getMessage().contains("at least 32 bytes"));
    }
}
