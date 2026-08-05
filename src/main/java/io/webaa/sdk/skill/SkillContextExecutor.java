package io.webaa.sdk.skill;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface SkillContextExecutor {
    CompletableFuture<Map<String, Object>> execute(
        Map<String, Object> params,
        SkillExecutionContext context
    );
}
