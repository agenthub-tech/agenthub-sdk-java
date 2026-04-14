package io.webaa.sdk.model;

import java.util.Collections;
import java.util.List;

/**
 * Options for {@link io.webaa.sdk.WebAASDK#init(InitOptions)}.
 */
public class InitOptions {

    private final String channelKey;
    private final List<SkillDefinition> skills;
    private final UserIdentity user;
    private final String apiBase;
    private final String protocolVersion;
    private final int maxRetries;
    private final long retryDelayMs;
    private final long heartbeatTimeoutMs;
    private final boolean debug;

    private InitOptions(Builder builder) {
        this.channelKey = builder.channelKey;
        this.skills = builder.skills;
        this.user = builder.user;
        this.apiBase = builder.apiBase;
        this.protocolVersion = builder.protocolVersion;
        this.maxRetries = builder.maxRetries;
        this.retryDelayMs = builder.retryDelayMs;
        this.heartbeatTimeoutMs = builder.heartbeatTimeoutMs;
        this.debug = builder.debug;
    }

    public String getChannelKey() { return channelKey; }
    public List<SkillDefinition> getSkills() { return skills; }
    public UserIdentity getUser() { return user; }
    public String getApiBase() { return apiBase; }
    public String getProtocolVersion() { return protocolVersion; }
    public int getMaxRetries() { return maxRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public long getHeartbeatTimeoutMs() { return heartbeatTimeoutMs; }
    public boolean isDebug() { return debug; }

    public static Builder builder(String channelKey) {
        return new Builder(channelKey);
    }

    public static class Builder {
        private final String channelKey;
        private List<SkillDefinition> skills = Collections.emptyList();
        private UserIdentity user;
        private String apiBase = "";
        private String protocolVersion = "1.0.0";
        private int maxRetries = 3;
        private long retryDelayMs = 1000;
        private long heartbeatTimeoutMs = 45000;
        private boolean debug = false;

        private Builder(String channelKey) {
            this.channelKey = channelKey;
        }

        public Builder skills(List<SkillDefinition> skills) { this.skills = skills; return this; }
        public Builder user(UserIdentity user) { this.user = user; return this; }
        public Builder apiBase(String apiBase) { this.apiBase = apiBase; return this; }
        public Builder protocolVersion(String v) { this.protocolVersion = v; return this; }
        public Builder maxRetries(int n) { this.maxRetries = n; return this; }
        public Builder retryDelayMs(long ms) { this.retryDelayMs = ms; return this; }
        public Builder heartbeatTimeoutMs(long ms) { this.heartbeatTimeoutMs = ms; return this; }
        public Builder debug(boolean debug) { this.debug = debug; return this; }

        public InitOptions build() { return new InitOptions(this); }
    }
}
