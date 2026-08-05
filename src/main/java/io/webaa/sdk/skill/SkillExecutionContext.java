package io.webaa.sdk.skill;

import java.util.concurrent.CompletableFuture;

public class SkillExecutionContext {
    private final String executionId;
    private final String runId;
    private final String toolCallId;
    private final SkillProgressReporter progressReporter;

    public SkillExecutionContext(String executionId, String runId, String toolCallId,
                                 SkillProgressReporter progressReporter) {
        this.executionId = executionId;
        this.runId = runId;
        this.toolCallId = toolCallId;
        this.progressReporter = progressReporter;
    }

    public String getExecutionId() { return executionId; }
    public String getRunId() { return runId; }
    public String getToolCallId() { return toolCallId; }
    public CompletableFuture<Void> reportProgress(java.util.Map<String, Object> progress) {
        return progressReporter.report(progress);
    }
}
