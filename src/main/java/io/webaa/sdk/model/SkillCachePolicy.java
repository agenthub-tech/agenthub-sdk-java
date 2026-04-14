package io.webaa.sdk.model;

import java.util.Collections;
import java.util.List;

/**
 * L1 SDK Auto Cache policy for a skill.
 */
public class SkillCachePolicy {

    public enum Mode { SNAPSHOT, APPEND, NONE }

    private final boolean enabled;
    private final long ttlMs;
    private final Mode mode;
    private final List<String> invalidateOn;

    public SkillCachePolicy(boolean enabled, long ttlMs, Mode mode, List<String> invalidateOn) {
        this.enabled = enabled;
        this.ttlMs = ttlMs;
        this.mode = mode;
        this.invalidateOn = invalidateOn != null ? invalidateOn : Collections.emptyList();
    }

    public static SkillCachePolicy disabled() {
        return new SkillCachePolicy(false, 0, Mode.NONE, Collections.emptyList());
    }

    public boolean isEnabled() { return enabled; }
    public long getTtlMs() { return ttlMs; }
    public Mode getMode() { return mode; }
    public List<String> getInvalidateOn() { return invalidateOn; }
}
