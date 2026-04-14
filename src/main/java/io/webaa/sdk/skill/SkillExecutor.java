package io.webaa.sdk.skill;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for skill execution logic.
 * Equivalent to JS SDK's {@code (params) => Promise<Record<string, unknown>>}.
 */
@FunctionalInterface
public interface SkillExecutor {

    /**
     * Execute the skill with the given parameters.
     *
     * @param params tool call parameters from the LLM
     * @return a future resolving to the execution result
     */
    CompletableFuture<Map<String, Object>> execute(Map<String, Object> params);
}
