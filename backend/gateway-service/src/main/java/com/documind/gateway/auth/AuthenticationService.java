package com.documind.gateway.auth;

import com.documind.common.domain.UserRole;
import com.documind.common.domain.WorkspacePlan;
import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.persistence.entity.UserEntity;
import com.documind.common.persistence.entity.WorkspaceEntity;
import com.documind.common.persistence.repository.UserRepository;
import com.documind.common.persistence.repository.WorkspaceRepository;
import com.documind.common.security.AuthenticatedUser;
import com.documind.common.security.JwtTokenService;
import com.documind.gateway.auth.dto.AuthenticationResponse;
import com.documind.gateway.auth.dto.LoginRequest;
import com.documind.gateway.auth.dto.RefreshRequest;
import com.documind.gateway.auth.dto.RegistrationRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthenticationService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthenticationResponse register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException("An account already exists for " + request.email());
        }

        Instant now = Instant.now();
        WorkspaceEntity workspace = workspaceRepository.save(
                new WorkspaceEntity(UUID.randomUUID(), request.workspaceName(), WorkspacePlan.FREE, now));
        UserEntity user = userRepository.save(new UserEntity(
                UUID.randomUUID(),
                request.email(),
                passwordEncoder.encode(request.password()),
                workspace.getId(),
                UserRole.ADMIN,
                now));

        return issueTokens(user);
    }

    @Transactional
    public AuthenticationResponse login(LoginRequest request) {
        UserEntity user = userRepository
                .findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthenticationResponse refresh(RefreshRequest request) {
        RefreshTokenRotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        UserEntity user = userRepository
                .findById(rotation.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User " + rotation.userId() + " no longer exists"));

        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getWorkspaceId(), user.getEmail(), user.getRole());
        return new AuthenticationResponse(
                tokenService.issueAccessToken(principal),
                rotation.refreshToken(),
                tokenService.accessTokenTtlSeconds(),
                user.getId(),
                user.getWorkspaceId(),
                user.getEmail(),
                user.getRole().name());
    }

    /** Ends the single session the caller presented a refresh token for. */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /** Ends every session for the account that owns the presented refresh token, for example
     * after a suspected compromise. Deliberately takes a refresh token rather than an arbitrary
     * user id, so a caller can only mass-revoke sessions they can prove they hold one of. */
    @Transactional
    public void logoutAllSessions(RefreshRequest request) {
        refreshTokenService.revokeAllSessions(request.refreshToken());
    }

    private AuthenticationResponse issueTokens(UserEntity user) {
        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getWorkspaceId(), user.getEmail(), user.getRole());
        return new AuthenticationResponse(
                tokenService.issueAccessToken(principal),
                refreshTokenService.issue(user.getId()),
                tokenService.accessTokenTtlSeconds(),
                user.getId(),
                user.getWorkspaceId(),
                user.getEmail(),
                user.getRole().name());
    }
}
