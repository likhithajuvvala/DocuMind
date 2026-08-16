package com.documind.common.persistence.repository;

import com.documind.common.persistence.entity.DocumentChunkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    List<DocumentChunkEntity> findByEmbeddingIdInAndWorkspaceId(
            List<String> embeddingIds, UUID workspaceId);

    List<DocumentChunkEntity> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
