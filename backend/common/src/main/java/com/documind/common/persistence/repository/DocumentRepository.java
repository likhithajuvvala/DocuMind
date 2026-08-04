package com.documind.common.persistence.repository;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.persistence.entity.DocumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Page<DocumentEntity> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Optional<DocumentEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<DocumentEntity> findByWorkspaceIdAndStatus(UUID workspaceId, DocumentStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, DocumentStatus status);
}
