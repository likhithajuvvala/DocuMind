package com.documind.common.security;

import com.documind.common.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

public class JwtTokenService {

    private static final String CLAIM_WORKSPACE = "workspace_id";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String TYPE_ACCESS = "access";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey =
                Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.userId().toString())
                .claim(CLAIM_WORKSPACE, user.workspaceId().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    public Optional<AuthenticatedUser> resolveAccessToken(String token) {
        return parse(token)
                .filter(claims -> TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class)))
                .map(
                        claims ->
                                new AuthenticatedUser(
                                        UUID.fromString(claims.getSubject()),
                                        UUID.fromString(claims.get(CLAIM_WORKSPACE, String.class)),
                                        claims.get(CLAIM_EMAIL, String.class),
                                        UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))));
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .requireIssuer(properties.getIssuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload());
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
