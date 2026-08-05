package io.webaa.sdk.model;

import io.webaa.sdk.skill.SkillExecutor;
import io.webaa.sdk.skill.SkillContextExecutor;

import java.util.List;
import java.util.Map;

/**
 * A skill definition to register with the backend.
 * Mirrors JS SDK's SkillDefinition interface.
 */
public class SkillDefinition {

    private final String name;
    private final Map<String, Object> schema;
    private final String promptInjection;
    private final String executionMode; // "sdk" | "backend"
    private final SkillExecutor executor;
    private final SkillContextExecutor contextExecutor;
    private final SkillCachePolicy cachePolicy;
    private final List<Map<String, Object>> resultCacheFields;
    private final List<String> nonSummaryResultFields;

    private SkillDefinition(Builder builder) {
        this.name = builder.name;
        this.schema = builder.schema;
        this.promptInjection = builder.promptInjection;
        this.executionMode = builder.executionMode;
        this.executor = builder.executor;
        this.contextExecutor = builder.contextExecutor;
        this.cachePolicy = builder.cachePolicy;
        this.resultCacheFields = builder.resultCacheFields;
        this.nonSummaryResultFields = builder.nonSummaryResultFields;
    }

    public String getName() { return name; }
    public Map<String, Object> getSchema() { return schema; }
    public String getPromptInjection() { return promptInjection; }
    public String getExecutionMode() { return executionMode; }
    public SkillExecutor getExecutor() { return executor; }
    public SkillContextExecutor getContextExecutor() { return contextExecutor; }
    public SkillCachePolicy getCachePolicy() { return cachePolicy; }
    public List<Map<String, Object>> getResultCacheFields() { return resultCacheFields; }
    public List<String> getNonSummaryResultFields() { return nonSummaryResultFields; }

    public static Builder builder(String name, Map<String, Object> schema, SkillExecutor executor) {
        return new Builder(name, schema, executor);
    }

    public static class Builder {
        private final String name;
        private final Map<String, Object> schema;
        private final SkillExecutor executor;
        private SkillContextExecutor contextExecutor;
        private String promptInjection;
        private String executionMode = "sdk";
        private SkillCachePolicy cachePolicy = SkillCachePolicy.disabled();
        private List<Map<String, Object>> resultCacheFields;
        private List<String> nonSummaryResultFields;

        private Builder(String name, Map<String, Object> schema, SkillExecutor executor) {
            this.name = name;
            this.schema = schema;
            this.executor = executor;
        }

        public Builder promptInjection(String promptInjection) {
            this.promptInjection = promptInjection;
            return this;
        }

        public Builder executionMode(String executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public Builder executeWithContext(SkillContextExecutor contextExecutor) {
            this.contextExecutor = contextExecutor;
            return this;
        }

        public Builder cachePolicy(SkillCachePolicy cachePolicy) {
            this.cachePolicy = cachePolicy;
            return this;
        }

        public Builder resultCacheFields(List<Map<String, Object>> resultCacheFields) {
            this.resultCacheFields = resultCacheFields;
            return this;
        }

        public Builder nonSummaryResultFields(List<String> nonSummaryResultFields) {
            this.nonSummaryResultFields = nonSummaryResultFields;
            return this;
        }

        public SkillDefinition build() {
            return new SkillDefinition(this);
        }
    }
}
