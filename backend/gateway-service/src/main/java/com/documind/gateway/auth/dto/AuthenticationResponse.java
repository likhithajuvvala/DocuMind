package com.documind.gateway.auth.dto;

import java.util.UUID;

public record AuthenticationResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID userId,
        UUID workspaceId,
        String email,
        String role) {}
