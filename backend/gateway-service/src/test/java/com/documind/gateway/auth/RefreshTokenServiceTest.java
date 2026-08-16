package com.documind.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documind.common.persistence.entity.RefreshTokenEntity;
import com.documind.common.persistence.repository.RefreshTokenRepository;
import com.documind.common.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(repository, jwtProperties());

    @Test
    void issueStoresOnlyTheHashOfTheTokenItReturns() throws Exception {
        UUID userId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(userId);

        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getTokenHash())
                .as("the raw token itself must never be persisted")
                .isNotEqualTo(rawToken)
                .isEqualTo(sha256Hex(rawToken));
    }

    @Test
    void rotateReplacesTheTokenWithANewOneInTheSameFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity current = activeToken(familyId);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenRotationResult result = service.rotate("presented-token");

        assertThat(result.userId()).isEqualTo(current.getUserId());
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(current.isRevoked()).as("the rotated-away token must be revoked immediately").isTrue();
        assertThat(current.getReplacedById()).isNotNull();

        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
                .as("the replacement token must stay in the same rotation family")
                .anySatisfy(entity -> assertThat(entity.getFamilyId()).isEqualTo(familyId));
    }

    @Test
    void rotateRejectsATokenThatWasNeverIssued() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsAnExpiredTokenWithoutTouchingTheFamily() {
        RefreshTokenEntity expired = new RefreshTokenEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hash",
                UUID.randomUUID(),
                Instant.now().minus(Duration.ofDays(20)),
                Instant.now().minus(Duration.ofDays(6)));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).findByFamilyIdAndRevokedAtIsNull(any());
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesEveryOtherTokenInTheFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity alreadyUsed = activeToken(familyId);
        alreadyUsed.revoke(Instant.now().minus(Duration.ofMinutes(5)));

        RefreshTokenEntity sibling = activeToken(familyId);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyUsed));
        when(repository.findByFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of(sibling));

        assertThatThrownBy(() -> service.rotate("stolen")).isInstanceOf(RefreshTokenReuseException.class);

        assertThat(sibling.isRevoked())
                .as("every live token descended from the same login must be killed, not just the reused one")
                .isTrue();
        verify(repository).saveAll(List.of(sibling));
    }

    @Test
    void revokeMarksAnActiveTokenRevoked() {
        RefreshTokenEntity token = activeToken(UUID.randomUUID());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        service.revoke("token");

        assertThat(token.isRevoked()).isTrue();
        verify(repository).save(token);
    }

    @Test
    void revokeIsANoOpForATokenThatIsAlreadyRevoked() {
        RefreshTokenEntity token = activeToken(UUID.randomUUID());
        token.revoke(Instant.now());
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        service.revoke("token");

        verify(repository, never()).save(any());
    }

    @Test
    void revokeIsANoOpForAnUnrecognisedToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("never-issued");

        verify(repository, never()).save(any());
    }

    @Test
    void revokeAllSessionsRevokesEveryActiveTokenOwnedByThatUser() {
        UUID userId = UUID.randomUUID();
        RefreshTokenEntity sessionOne = activeTokenFor(userId);
        RefreshTokenEntity sessionTwo = activeTokenFor(userId);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(sessionOne));
        when(repository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(sessionOne, sessionTwo));

        Optional<UUID> result = service.revokeAllSessions("token");

        assertThat(result).contains(sessionOne.getUserId());
        assertThat(sessionOne.isRevoked()).isTrue();
        assertThat(sessionTwo.isRevoked()).isTrue();
        verify(repository).saveAll(List.of(sessionOne, sessionTwo));
    }

    @Test
    void revokeAllSessionsReturnsEmptyForAnUnrecognisedToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.revokeAllSessions("never-issued")).isEmpty();
        verify(repository, never()).findByUserIdAndRevokedAtIsNull(any());
    }

    private RefreshTokenEntity activeToken(UUID familyId) {
        return new RefreshTokenEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hash-" + UUID.randomUUID(),
                familyId,
                Instant.now(),
                Instant.now().plus(Duration.ofDays(14)));
    }

    private RefreshTokenEntity activeTokenFor(UUID userId) {
        return new RefreshTokenEntity(
                UUID.randomUUID(),
                userId,
                "hash-" + UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now().plus(Duration.ofDays(14)));
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-value-that-is-long-enough-for-hmac-sha-256");
        properties.setIssuer("documind");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(14));
        return properties;
    }
}
