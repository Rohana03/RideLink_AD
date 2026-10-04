package lk.sliit.ridelink.ride.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtUtilTest {

    private static final String SECRET = "test-only-jwt-secret-key-not-used-outside-unit-tests-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    /** Builds a token the way the Account Service does. */
    private static String accountServiceToken(String secret, long ttlMs) {
        return Jwts.builder()
                .subject("driver-user-1")
                .claim("userId", "driver-user-1")
                .claim("roles", List.of("DRIVER"))
                .issuer("ridelink-account-service")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void readsUserIdAndRolesFromAnAccountServiceToken() {
        String token = accountServiceToken(SECRET, 60_000);

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("driver-user-1");
        assertThat(jwtUtil.extractRoles(token)).containsExactly("DRIVER");
    }

    @Test
    void rejectsTokensSignedWithAnotherSecretExpiredOrMalformed() {
        assertThat(jwtUtil.isTokenValid(accountServiceToken("another-secret-that-is-also-long-enough-0123456789", 60_000))).isFalse();
        assertThat(jwtUtil.isTokenValid(accountServiceToken(SECRET, -1_000))).isFalse();
        assertThat(jwtUtil.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void refusesASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtUtil("too-short-secret")).isInstanceOf(IllegalStateException.class);
    }
}
