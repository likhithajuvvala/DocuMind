package com.documind.gateway.auth;

import com.documind.common.persistence.entity.RefreshTokenEntity;
import com.documind.common.persistence.repository.RefreshTokenRepository;
import com.documind.common.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes refresh tokens. Tokens are opaque random strings, not JWTs: the
 * database row backing a token is the only source of truth for whether it is still valid, which
 * is what makes revocation possible at all. Only the SHA-256 hash of a token is ever persisted.
 *
 * <p>Every token issued from the same login shares a {@code familyId}. Rotation replaces a token
 * with a new one in the same family; presenting a token a second time after it has already been
 * rotated revokes the entire family, since that can only happen if the token was copied.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        saveNewToken(userId, UUID.randomUUID(), rawToken);
        return rawToken;
    }

    // Revoking the family is the entire point of detecting reuse, so that side effect must survive
    // even though the method also throws. Spring's default rollback-on-RuntimeException would
    // otherwise silently undo the revocation along with everything else in this transaction — and
    // noRollbackFor alone isn't enough, because the caller (AuthenticationService.refresh) has its
    // own plain @Transactional boundary that participates in the same physical transaction and
    // marks it rollback-only regardless of this method's more permissive annotation. REQUIRES_NEW
    // gives this method its own independent transaction that commits on its own terms.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RefreshTokenReuseException.class)
    public RefreshTokenRotationResult rotate(String presentedToken) {
        RefreshTokenEntity current = repository
                .findByTokenHash(hash(presentedToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            revokeFamily(current.getFamilyId());
            throw new RefreshTokenReuseException();
        }
        if (current.isExpired(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        String nextRaw = generateRawToken();
        RefreshTokenEntity next = saveNewToken(current.getUserId(), current.getFamilyId(), nextRaw);
        current.markReplacedBy(next.getId(), Instant.now());
        repository.save(current);

        return new RefreshTokenRotationResult(current.getUserId(), nextRaw);
    }

    /** Revokes exactly the presented token. Unknown or already-revoked tokens are a no-op:
     * logging out ends with the same guarantee (this token can no longer be used) either way,
     * and there is nothing useful to tell the caller by distinguishing the two. */
    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHash(hash(presentedToken)).filter(token -> !token.isRevoked()).ifPresent(token -> {
            token.revoke(Instant.now());
            repository.save(token);
        });
    }

    /** Revokes every active session belonging to whoever owns the presented token, not just that
     * one token. Returns the affected user id, or empty if the token was not recognised. */
    @Transactional
    public Optional<UUID> revokeAllSessions(String presentedToken) {
        return repository.findByTokenHash(hash(presentedToken)).map(token -> {
            revokeAllActiveTokensFor(token.getUserId());
            return token.getUserId();
        });
    }

    private void revokeAllActiveTokensFor(UUID userId) {
        Instant now = Instant.now();
        List<RefreshTokenEntity> active = repository.findByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revoke(now));
        repository.saveAll(active);
    }

    private void revokeFamily(UUID familyId) {
        Instant now = Instant.now();
        List<RefreshTokenEntity> active = repository.findByFamilyIdAndRevokedAtIsNull(familyId);
        active.forEach(token -> token.revoke(now));
        repository.saveAll(active);
    }

    private RefreshTokenEntity saveNewToken(UUID userId, UUID familyId, String rawToken) {
        Instant now = Instant.now();
        return repository.save(new RefreshTokenEntity(
                UUID.randomUUID(),
                userId,
                hash(rawToken),
                familyId,
                now,
                now.plus(jwtProperties.getRefreshTokenTtl())));
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(HASH_ALGORITHM + " must be available on every JVM", exception);
        }
    }
}
