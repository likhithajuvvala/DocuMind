package com.documind.query.api;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.persistence.projection.UsageSummary;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.UsageLogRepository;
import com.documind.common.security.AuthenticatedUser;
import com.documind.common.security.CurrentUser;
import com.documind.query.api.dto.IngestionHealthResponse;
import com.documind.query.api.dto.WorkspaceUsageResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final UsageLogRepository usageLogRepository;
    private final DocumentRepository documentRepository;

    public AdminController(
            UsageLogRepository usageLogRepository, DocumentRepository documentRepository) {
        this.usageLogRepository = usageLogRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/usage")
    public WorkspaceUsageResponse usage(
            @RequestParam(defaultValue = "" + DEFAULT_WINDOW_DAYS) int windowDays) {
        AuthenticatedUser user = CurrentUser.require();
        Instant since = Instant.now().minus(Duration.ofDays(windowDays));
        List<UsageSummary> perUser = usageLogRepository.summarizeByUser(user.workspaceId(), since);

        long totalTokens = perUser.stream().mapToLong(UsageSummary::totalTokens).sum();
        BigDecimal totalCost =
                perUser.stream()
                        .map(UsageSummary::totalCost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WorkspaceUsageResponse(
                user.workspaceId(), since, totalTokens, totalCost, perUser);
    }

    @GetMapping("/documents/status")
    public IngestionHealthResponse ingestionHealth() {
        AuthenticatedUser user = CurrentUser.require();
        return new IngestionHealthResponse(
                documentRepository.countByWorkspaceIdAndStatus(
                        user.workspaceId(), DocumentStatus.PENDING),
                documentRepository.countByWorkspaceIdAndStatus(
                        user.workspaceId(), DocumentStatus.PROCESSING),
                documentRepository.countByWorkspaceIdAndStatus(
                        user.workspaceId(), DocumentStatus.INDEXED),
                documentRepository.countByWorkspaceIdAndStatus(
                        user.workspaceId(), DocumentStatus.FAILED));
    }
}
