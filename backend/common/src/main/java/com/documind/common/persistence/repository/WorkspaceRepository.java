package com.documind.common.persistence.repository;

import com.documind.common.persistence.entity.WorkspaceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
}
