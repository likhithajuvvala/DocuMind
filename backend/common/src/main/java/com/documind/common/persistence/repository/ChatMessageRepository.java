package com.documind.common.persistence.repository;

import com.documind.common.persistence.entity.ChatMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtDesc(UUID sessionId, Pageable pageable);
}
