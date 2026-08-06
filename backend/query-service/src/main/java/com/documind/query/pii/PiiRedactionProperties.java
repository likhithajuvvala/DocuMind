package com.documind.query.pii;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.pii")
public class PiiRedactionProperties {

    public enum Mode {
        THIRD_PARTY_ONLY,
        ALWAYS,
        NEVER
    }

    private Mode mode = Mode.THIRD_PARTY_ONLY;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
