package com.documind.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.gateway.auth.dto.AuthenticationResponse;
import com.documind.gateway.auth.dto.LoginRequest;
import com.documind.gateway.auth.dto.RefreshRequest;
import com.documind.gateway.auth.dto.RegistrationRequest;
import com.documind.common.web.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Drives {@code /api/auth/*} exactly as a browser would, over real HTTP against a real Postgres
 * database with Flyway-migrated schema. This is what actually proves rotation and reuse
 * detection work, since {@link RefreshTokenServiceTest} only proves the logic is correct against
 * a mock, not that the wiring (transactions, the unique constraint on token_hash, the
 * exception-to-HTTP-status mapping) holds together end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthenticationFlowIntegrationTest {

    // Must be pgvector-enabled: V1's migration runs `create extension if not exists vector`, which
    // a plain postgres image cannot satisfy.
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("documind")
            .withUsername("documind")
            .withPassword("documind");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rotationIssuesAFreshTokenAndTheOldOneCanNeverBeUsedAgain() {
        AuthenticationResponse original = register("rotation@documind.test");

        ResponseEntity<AuthenticationResponse> refreshed =
                restTemplate.postForEntity("/api/auth/refresh", new RefreshRequest(original.refreshToken()), AuthenticationResponse.class);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().refreshToken())
                .as("refresh must rotate, not just reissue the same token")
                .isNotEqualTo(original.refreshToken());

        // Replaying the original (now-superseded) token must fail, and must reveal that reuse
        // was detected rather than just "invalid", since that is the signal an attacker replayed
        // a stolen token.
        ResponseEntity<ApiErrorResponse> reuseAttempt = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(original.refreshToken()), ApiErrorResponse.class);
        assertThat(reuseAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuseAttempt.getBody().code()).isEqualTo("refresh_token_reused");

        // The replacement token must also be dead: reuse detection kills the whole chain, not
        // just the token that was actually replayed.
        ResponseEntity<ApiErrorResponse> chainKilled = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(refreshed.getBody().refreshToken()), ApiErrorResponse.class);
        assertThat(chainKilled.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aNeverIssuedTokenIsRejectedAsInvalidNotAsReused() {
        ResponseEntity<ApiErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest("this-was-never-issued-by-the-server"), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("invalid_refresh_token");
    }

    @Test
    void logoutRevokesTheTokenSoItCannotBeUsedAgain() {
        AuthenticationResponse session = register("logout@documind.test");

        ResponseEntity<Void> logoutResponse =
                restTemplate.postForEntity("/api/auth/logout", new RefreshRequest(session.refreshToken()), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> afterLogout = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(session.refreshToken()), ApiErrorResponse.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutIsIdempotentAndNeverLeaksWhetherATokenExisted() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/auth/logout", new RefreshRequest("something-that-was-never-issued"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logoutAllRevokesEverySessionForTheAccountNotJustTheOnePresented() {
        String email = "everywhere@documind.test";
        AuthenticationResponse firstDeviceSession = register(email);
        AuthenticationResponse secondDeviceSession = login(email);

        assertThat(secondDeviceSession.refreshToken()).isNotEqualTo(firstDeviceSession.refreshToken());

        ResponseEntity<Void> logoutAll = restTemplate.postForEntity(
                "/api/auth/logout-all", new RefreshRequest(secondDeviceSession.refreshToken()), Void.class);
        assertThat(logoutAll.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiErrorResponse> firstDeviceAfter = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(firstDeviceSession.refreshToken()), ApiErrorResponse.class);
        assertThat(firstDeviceAfter.getStatusCode())
                .as("logout-all must reach every session for the account, not only the family of the presented token")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ApiErrorResponse> secondDeviceAfter = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(secondDeviceSession.refreshToken()), ApiErrorResponse.class);
        assertThat(secondDeviceAfter.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AuthenticationResponse register(String email) {
        RegistrationRequest request = new RegistrationRequest(email, "a-long-enough-password", "Workspace for " + email);
        ResponseEntity<AuthenticationResponse> response =
                restTemplate.postForEntity("/api/auth/register", request, AuthenticationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private AuthenticationResponse login(String email) {
        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "a-long-enough-password"), AuthenticationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
