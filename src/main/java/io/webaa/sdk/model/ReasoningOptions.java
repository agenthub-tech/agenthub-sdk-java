package io.webaa.sdk.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reasoning options for the main runner only.
 */
public class ReasoningOptions {

    private final String mode;

    private ReasoningOptions(Builder builder) {
        this.mode = builder.mode;
    }

    public String getMode() { return mode; }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", mode);
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String mode = "default";

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public ReasoningOptions build() {
            return new ReasoningOptions(this);
        }
    }
}
