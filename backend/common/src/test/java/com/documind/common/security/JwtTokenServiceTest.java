package com.documind.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.common.domain.UserRole;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private final JwtTokenService tokenService = new JwtTokenService(properties());

    @Test
    void issuesAccessTokenCarryingWorkspaceAndRole() {
        AuthenticatedUser user =
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "user@documind.test", UserRole.ADMIN);

        String token = tokenService.issueAccessToken(user);

        assertThat(tokenService.resolveAccessToken(token)).contains(user);
    }

    @Test
    void rejectsAccessTokenWhenSignatureDoesNotMatch() {
        AuthenticatedUser user =
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "user@documind.test", UserRole.MEMBER);
        String token = tokenService.issueAccessToken(user);

        JwtProperties otherProperties = properties();
        otherProperties.setSecret("another-secret-value-that-is-long-enough-for-hmac-sha");
        JwtTokenService otherService = new JwtTokenService(otherProperties);

        assertThat(otherService.resolveAccessToken(token)).isEmpty();
    }

    @Test
    void doesNotAcceptRefreshTokenAsAccessToken() {
        AuthenticatedUser user =
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "user@documind.test", UserRole.MEMBER);

        String refreshToken = tokenService.issueRefreshToken(user);

        assertThat(tokenService.resolveAccessToken(refreshToken)).isEmpty();
        assertThat(tokenService.resolveRefreshToken(refreshToken)).contains(user.userId());
    }

    private JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-value-that-is-long-enough-for-hmac-sha-256");
        properties.setIssuer("documind");
        properties.setAccessTokenTtl(Duration.ofMinutes(30));
        properties.setRefreshTokenTtl(Duration.ofDays(1));
        return properties;
    }
}
