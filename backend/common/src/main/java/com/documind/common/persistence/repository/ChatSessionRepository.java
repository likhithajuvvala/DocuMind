package com.documind.common.persistence.repository;

import com.documind.common.persistence.entity.ChatSessionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    Optional<ChatSessionEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ChatSessionEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
