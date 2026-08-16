package com.documind.query.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documind.common.domain.UserRole;
import com.documind.common.persistence.entity.UsageLogEntity;
import com.documind.common.persistence.repository.UsageLogRepository;
import com.documind.common.security.AuthenticatedUser;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;

class UsageRecorderTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    private final UsageLogRepository usageLogRepository = mock(UsageLogRepository.class);
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final UsageRecorder recorder = new UsageRecorder(usageLogRepository, pricing(), registry);

    @Test
    void exposesTheSeriesNamesTheOverviewDashboardQueries() {
        when(usageLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.record(user(), "a question about the vendor agreement", "an answer grounded in the excerpts");

        String scrape = registry.scrape();

        assertThat(scrape).contains("documind_llm_tokens_total");
        assertThat(scrape).contains("documind_llm_cost_total");
        assertThat(scrape).contains("kind=\"prompt\"");
        assertThat(scrape).contains("kind=\"completion\"");
        assertThat(scrape).contains("workspace=\"" + WORKSPACE_ID + "\"");
    }

    @Test
    void reportsTheSameTokenTotalItPersists() {
        when(usageLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int reported = recorder.record(user(), "question", "answer");

        double counted = registry.find("documind.llm.tokens").counters().stream()
                .mapToDouble(counter -> counter.count())
                .sum();
        assertThat((double) reported).isEqualTo(counted);
    }

    @Test
    void storesUsageAgainstTheCallersWorkspace() {
        when(usageLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.record(user(), "question", "answer");

        org.mockito.ArgumentCaptor<UsageLogEntity> saved = org.mockito.ArgumentCaptor.forClass(UsageLogEntity.class);
        org.mockito.Mockito.verify(usageLogRepository).save(saved.capture());
        assertThat(saved.getValue().getWorkspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void prefersTheProvidersReportedTokenCountsOverTheCharacterEstimate() {
        when(usageLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // "a question about the vendor agreement" is 38 characters, which the char/4 estimate
        // would turn into 9 tokens; the provider's real count must win instead.
        Usage reportedUsage = new DefaultUsage(41, 17);

        recorder.record(
                user(), "a question about the vendor agreement", "an answer grounded in the excerpts", reportedUsage);

        org.mockito.ArgumentCaptor<UsageLogEntity> saved = org.mockito.ArgumentCaptor.forClass(UsageLogEntity.class);
        org.mockito.Mockito.verify(usageLogRepository).save(saved.capture());
        assertThat(saved.getValue().getPromptTokens()).isEqualTo(41);
        assertThat(saved.getValue().getCompletionTokens()).isEqualTo(17);
    }

    @Test
    void fallsBackToTheCharacterEstimateWhenTheProviderReportedNoUsage() {
        when(usageLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.record(user(), "question", "answer", new EmptyUsage());

        org.mockito.ArgumentCaptor<UsageLogEntity> saved = org.mockito.ArgumentCaptor.forClass(UsageLogEntity.class);
        org.mockito.Mockito.verify(usageLogRepository).save(saved.capture());
        assertThat(saved.getValue().getPromptTokens()).isGreaterThan(0);
        assertThat(saved.getValue().getCompletionTokens()).isGreaterThan(0);
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), WORKSPACE_ID, "demo@documind.test", UserRole.ADMIN);
    }

    private ModelPricingProperties pricing() {
        ModelPricingProperties properties = new ModelPricingProperties();
        properties.setModelName("gpt-4o-mini");
        return properties;
    }
}
