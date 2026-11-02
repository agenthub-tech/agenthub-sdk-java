package io.webaa.sdk.model;

import java.util.Map;

/**
 * End-user identity for the identify() call.
 */
public class UserIdentity {

    private final String userId;
    private final String account;
    private final String name;
    private final String avatar;
    private final Map<String, Object> metadata;

    public UserIdentity(String userId) {
        this(userId, null, null, null);
    }

    public UserIdentity(String userId, String name, String avatar, Map<String, Object> metadata) {
        this(userId, name, avatar, metadata, null);
    }

    public UserIdentity(String userId, String account) {
        this(userId, null, null, null, account);
    }

    public UserIdentity(String userId, String name, String avatar, Map<String, Object> metadata, String account) {
        this.userId = userId;
        this.account = account;
        this.name = name;
        this.avatar = avatar;
        this.metadata = metadata;
    }

    public String getUserId() { return userId; }
    public String getAccount() { return account; }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
    public Map<String, Object> getMetadata() { return metadata; }
}
