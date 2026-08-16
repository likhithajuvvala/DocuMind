package com.documind.gateway.auth;

import java.util.UUID;

public record RefreshTokenRotationResult(UUID userId, String refreshToken) {}
