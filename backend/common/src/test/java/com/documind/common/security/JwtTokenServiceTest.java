package com.documind.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.common.domain.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private final JwtTokenService tokenService = new JwtTokenService(properties());

    @Test
    void issuesAccessTokenCarryingWorkspaceAndRole() {
        AuthenticatedUser user =
                new AuthenticatedUser(
                        UUID.randomUUID(), UUID.randomUUID(), "user@documind.test", UserRole.ADMIN);

        String token = tokenService.issueAccessToken(user);

        assertThat(tokenService.resolveAccessToken(token)).contains(user);
    }

    @Test
    void rejectsAccessTokenWhenSignatureDoesNotMatch() {
        AuthenticatedUser user =
                new AuthenticatedUser(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "user@documind.test",
                        UserRole.MEMBER);
        String token = tokenService.issueAccessToken(user);

        JwtProperties otherProperties = properties();
        otherProperties.setSecret("another-secret-value-that-is-long-enough-for-hmac-sha");
        JwtTokenService otherService = new JwtTokenService(otherProperties);

        assertThat(otherService.resolveAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsATokenThatIsNotSignedByJwtTokenService() {
        // A hand-crafted token with no token_type claim at all must not authenticate, since
        // resolveAccessToken only accepts tokens explicitly marked as the access type.
        JwtProperties properties = properties();
        SecretKey signingKey =
                Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        String bareToken =
                Jwts.builder()
                        .issuer(properties.getIssuer())
                        .subject(UUID.randomUUID().toString())
                        .signWith(signingKey)
                        .compact();

        assertThat(tokenService.resolveAccessToken(bareToken)).isEmpty();
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
