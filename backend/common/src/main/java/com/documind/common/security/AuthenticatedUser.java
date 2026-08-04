package com.documind.common.security;

import com.documind.common.domain.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, UUID workspaceId, String email, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
