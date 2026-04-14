package io.webaa.sdk.model;

import java.util.Map;

/**
 * Channel configuration fetched from GET /api/config.
 */
public class ChannelConfig {

    private String channelId;
    private String name;
    private Map<String, Object> permissionScope;
    private Map<String, Object> uiTheme;

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getPermissionScope() { return permissionScope; }
    public void setPermissionScope(Map<String, Object> permissionScope) { this.permissionScope = permissionScope; }

    public Map<String, Object> getUiTheme() { return uiTheme; }
    public void setUiTheme(Map<String, Object> uiTheme) { this.uiTheme = uiTheme; }
}
