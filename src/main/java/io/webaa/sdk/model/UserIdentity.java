package io.webaa.sdk.model;

import java.util.Map;

/**
 * End-user identity for the identify() call.
 */
public class UserIdentity {

    private final String userId;
    private final String name;
    private final String avatar;
    private final Map<String, Object> metadata;

    public UserIdentity(String userId) {
        this(userId, null, null, null);
    }

    public UserIdentity(String userId, String name, String avatar, Map<String, Object> metadata) {
        this.userId = userId;
        this.name = name;
        this.avatar = avatar;
        this.metadata = metadata;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
    public Map<String, Object> getMetadata() { return metadata; }
}
