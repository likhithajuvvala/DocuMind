/**
 * Declares the {@code workspaceFilter} Hibernate filter shared by every workspace-scoped entity in
 * this package. Each entity opts in with {@code @Filter(name = "workspaceFilter")}; the filter
 * itself is enabled per-transaction by {@code WorkspaceScopedTransactionManager}, scoped to
 * whatever workspace {@code WorkspaceContext} currently holds. {@code applyToLoadByKey = true} is
 * what makes this apply even to a plain {@code findById} — the exact case that made per-call-site
 * discipline ("remember to add AndWorkspaceId") an incomplete defense.
 */
@FilterDef(
        name = "workspaceFilter",
        parameters = @ParamDef(name = "workspaceId", type = UUID.class),
        defaultCondition = "workspace_id = :workspaceId",
        applyToLoadByKey = true)
package com.documind.common.persistence.entity;

import java.util.UUID;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
