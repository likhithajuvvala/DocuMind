package com.documind.common.tenant;

import java.util.Optional;
import java.util.UUID;

public final class WorkspaceContext {

    private static final ThreadLocal<UUID> CURRENT_WORKSPACE = new ThreadLocal<>();

    private WorkspaceContext() {}

    public static void set(UUID workspaceId) {
        CURRENT_WORKSPACE.set(workspaceId);
    }

    public static Optional<UUID> find() {
        return Optional.ofNullable(CURRENT_WORKSPACE.get());
    }

    public static UUID require() {
        return find().orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No workspace bound to the current request"));
    }

    public static void clear() {
        CURRENT_WORKSPACE.remove();
    }
}
