package io.webaa.sdk.model;

import java.util.Map;

/**
 * Options for {@link io.webaa.sdk.WebAASDK#run(RunOptions)}.
 */
public class RunOptions {

    private final String userInput;
    private final Map<String, Object> context;
    private final String threadId;
    private final String runId;
    private final Map<String, Object> toolResult;

    private RunOptions(Builder builder) {
        this.userInput = builder.userInput;
        this.context = builder.context;
        this.threadId = builder.threadId;
        this.runId = builder.runId;
        this.toolResult = builder.toolResult;
    }

    public String getUserInput() { return userInput; }
    public Map<String, Object> getContext() { return context; }
    public String getThreadId() { return threadId; }
    public String getRunId() { return runId; }
    public Map<String, Object> getToolResult() { return toolResult; }

    public static Builder builder(String userInput) {
        return new Builder(userInput);
    }

    public static class Builder {
        private final String userInput;
        private Map<String, Object> context;
        private String threadId;
        private String runId;
        private Map<String, Object> toolResult;

        private Builder(String userInput) {
            this.userInput = userInput;
        }

        public Builder context(Map<String, Object> context) { this.context = context; return this; }
        public Builder threadId(String threadId) { this.threadId = threadId; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder toolResult(Map<String, Object> toolResult) { this.toolResult = toolResult; return this; }

        public RunOptions build() { return new RunOptions(this); }
    }
}
