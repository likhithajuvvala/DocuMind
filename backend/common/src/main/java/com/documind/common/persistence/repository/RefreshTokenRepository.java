package com.documind.common.persistence.repository;

import com.documind.common.persistence.entity.RefreshTokenEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}
