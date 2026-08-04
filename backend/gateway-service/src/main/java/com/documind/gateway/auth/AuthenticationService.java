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

    public AuthenticationService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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

    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        UserEntity user = userRepository
                .findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthenticationResponse refresh(RefreshRequest request) {
        UUID userId = tokenService
                .resolveRefreshToken(request.refreshToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " no longer exists"));

        return issueTokens(user);
    }

    private AuthenticationResponse issueTokens(UserEntity user) {
        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getWorkspaceId(), user.getEmail(), user.getRole());
        return new AuthenticationResponse(
                tokenService.issueAccessToken(principal),
                tokenService.issueRefreshToken(principal),
                tokenService.accessTokenTtlSeconds(),
                user.getId(),
                user.getWorkspaceId(),
                user.getEmail(),
                user.getRole().name());
    }
}
