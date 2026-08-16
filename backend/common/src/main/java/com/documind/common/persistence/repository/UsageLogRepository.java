package com.documind.common.persistence.repository;

import com.documind.common.persistence.entity.UsageLogEntity;
import com.documind.common.persistence.projection.UsageSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageLogRepository extends JpaRepository<UsageLogEntity, UUID> {

    @Query(
            """
            select new com.documind.common.persistence.projection.UsageSummary(
                u.userId,
                sum(u.promptTokens + u.completionTokens),
                sum(u.costEstimate))
            from UsageLogEntity u
            where u.workspaceId = :workspaceId and u.createdAt >= :since
            group by u.userId
            """)
    List<UsageSummary> summarizeByUser(
            @Param("workspaceId") UUID workspaceId, @Param("since") Instant since);
}
