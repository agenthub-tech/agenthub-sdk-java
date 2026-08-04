package io.webaa.sdk.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final String runtimeMode;
    private final String instanceId;
    private final String providerId;
    private final int capacity;
    private final String runtime;
    private final Map<String, Object> metadata;

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
        this.runtimeMode = builder.runtimeMode;
        this.instanceId = builder.instanceId;
        this.providerId = builder.providerId;
        this.capacity = builder.capacity;
        this.runtime = builder.runtime;
        this.metadata = builder.metadata;
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
    public String getRuntimeMode() { return runtimeMode; }
    public String getInstanceId() { return instanceId; }
    public String getProviderId() { return providerId; }
    public int getCapacity() { return capacity; }
    public String getRuntime() { return runtime; }
    public Map<String, Object> getMetadata() { return metadata; }

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
        private String runtimeMode = "agent";
        private String instanceId;
        private String providerId;
        private int capacity = 1;
        private String runtime = "java";
        private Map<String, Object> metadata = Collections.emptyMap();

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
        public Builder runtimeMode(String mode) { this.runtimeMode = mode; return this; }
        public Builder instanceId(String id) { this.instanceId = id; return this; }
        public Builder providerId(String id) { this.providerId = id; return this; }
        public Builder capacity(int capacity) { this.capacity = capacity; return this; }
        public Builder runtime(String runtime) { this.runtime = runtime; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public InitOptions build() { return new InitOptions(this); }
    }
}
