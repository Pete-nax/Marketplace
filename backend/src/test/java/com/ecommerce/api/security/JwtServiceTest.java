package com.ecommerce.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — no Spring context needed. JwtService's @Value fields are
 * injected manually via ReflectionTestUtils since we're constructing it directly.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "test-only-secret-key-must-be-at-least-256-bits-long-for-hmac-sha");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", 900_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiryMs", 604_800_000L);
    }

    private UserDetails testUser() {
        return User.withUsername("jane@example.com")
                .password("irrelevant-for-this-test")
                .authorities("ROLE_CUSTOMER")
                .build();
    }

    @Test
    void generatedAccessToken_encodesCorrectSubjectAndUserId() {
        UserDetails user = testUser();

        String token = jwtService.generateAccessToken(user, 42L, "CUSTOMER");

        assertThat(jwtService.extractUsername(token)).isEqualTo("jane@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void isTokenValid_returnsTrueForMatchingUser() {
        UserDetails user = testUser();
        String token = jwtService.generateAccessToken(user, 1L, "CUSTOMER");

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseWhenSubjectDoesNotMatch() {
        UserDetails issuedTo = testUser();
        String token = jwtService.generateAccessToken(issuedTo, 1L, "CUSTOMER");

        UserDetails someoneElse = User.withUsername("mallory@example.com")
                .password("x").authorities("ROLE_CUSTOMER").build();

        assertThat(jwtService.isTokenValid(token, someoneElse)).isFalse();
    }

    @Test
    void refreshToken_doesNotCarryRoleOrUserIdClaims() {
        // Refresh tokens should be minimal — no role escalation surface if leaked
        UserDetails user = testUser();
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("jane@example.com");
        assertThat(jwtService.extractUserId(refreshToken)).isNull();
    }
}
