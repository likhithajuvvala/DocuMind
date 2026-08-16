package com.documind.query.pii;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiRedactionPolicyTest {

    @Test
    void redactsForAHostedProviderByDefault() {
        assertThat(policy(PiiRedactionProperties.Mode.THIRD_PARTY_ONLY, "openai").shouldRedact())
                .isTrue();
        assertThat(policy(PiiRedactionProperties.Mode.THIRD_PARTY_ONLY, "anthropic").shouldRedact())
                .isTrue();
    }

    @Test
    void leavesLocallyHostedModelsAloneByDefault() {
        assertThat(policy(PiiRedactionProperties.Mode.THIRD_PARTY_ONLY, "ollama").shouldRedact())
                .as(
                        "a local model never sends the text anywhere, so redacting only degrades the answer")
                .isFalse();
    }

    @Test
    void alwaysAndNeverOverrideTheProvider() {
        assertThat(policy(PiiRedactionProperties.Mode.ALWAYS, "ollama").shouldRedact()).isTrue();
        assertThat(policy(PiiRedactionProperties.Mode.NEVER, "openai").shouldRedact()).isFalse();
    }

    @Test
    void treatsAnUnknownProviderAsThirdParty() {
        assertThat(
                        policy(PiiRedactionProperties.Mode.THIRD_PARTY_ONLY, "some-new-vendor")
                                .shouldRedact())
                .as("failing open would leak to a provider nobody classified yet")
                .isTrue();
    }

    private PiiRedactionPolicy policy(PiiRedactionProperties.Mode mode, String provider) {
        PiiRedactionProperties properties = new PiiRedactionProperties();
        properties.setMode(mode);
        return new PiiRedactionPolicy(properties, provider);
    }
}
