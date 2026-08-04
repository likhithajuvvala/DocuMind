package com.documind.common.persistence.repository;

import com.documind.common.domain.IngestionStatus;
import com.documind.common.persistence.entity.IngestionJobEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJobEntity, UUID> {

    Optional<IngestionJobEntity> findFirstByDocumentIdOrderByStartedAtDesc(UUID documentId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, IngestionStatus status);
}
