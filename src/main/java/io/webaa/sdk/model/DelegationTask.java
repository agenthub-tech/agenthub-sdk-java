package io.webaa.sdk.model;

import java.util.Collections;
import java.util.Map;

public class DelegationTask {
    private final String delegationRunId;
    private final String targetSkill;
    private final Map<String, Object> params;
    private final String sourceRunId;
    private final String sourceChannelId;
    private final String targetChannelId;
    private final Map<String, Object> actor;
    private final Map<String, Object> identity;
    private final Map<String, Object> clientContext;

    public DelegationTask(
            String delegationRunId,
            String targetSkill,
            Map<String, Object> params,
            String sourceRunId,
            String sourceChannelId,
            String targetChannelId,
            Map<String, Object> actor,
            Map<String, Object> identity,
            Map<String, Object> clientContext) {
        this.delegationRunId = delegationRunId;
        this.targetSkill = targetSkill;
        this.params = params != null ? params : Collections.<String, Object>emptyMap();
        this.sourceRunId = sourceRunId;
        this.sourceChannelId = sourceChannelId;
        this.targetChannelId = targetChannelId;
        this.actor = actor != null ? actor : Collections.<String, Object>emptyMap();
        this.identity = identity != null ? identity : Collections.<String, Object>emptyMap();
        this.clientContext = clientContext != null ? clientContext : Collections.<String, Object>emptyMap();
    }

    public String getDelegationRunId() { return delegationRunId; }
    public String getTargetSkill() { return targetSkill; }
    public Map<String, Object> getParams() { return params; }
    public String getSourceRunId() { return sourceRunId; }
    public String getSourceChannelId() { return sourceChannelId; }
    public String getTargetChannelId() { return targetChannelId; }
    public Map<String, Object> getActor() { return actor; }
    public Map<String, Object> getIdentity() { return identity; }
    public Map<String, Object> getClientContext() { return clientContext; }
}
