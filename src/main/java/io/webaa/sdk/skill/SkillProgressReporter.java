package io.webaa.sdk.skill;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface SkillProgressReporter {
    CompletableFuture<Void> report(Map<String, Object> progress);
}
