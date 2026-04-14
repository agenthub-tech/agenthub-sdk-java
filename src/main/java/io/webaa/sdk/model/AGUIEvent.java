package io.webaa.sdk.model;

import java.util.Map;

/**
 * A single AG-UI protocol event received from the SSE stream.
 */
public class AGUIEvent {

    private String type;
    private Map<String, Object> payload;
    private String protocolVersion;
    private String timestamp;

    public AGUIEvent() {}

    public AGUIEvent(String type, Map<String, Object> payload, String protocolVersion, String timestamp) {
        this.type = type;
        this.payload = payload;
        this.protocolVersion = protocolVersion;
        this.timestamp = timestamp;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    /**
     * Convenience: get a string value from payload.
     */
    public String payloadString(String key) {
        if (payload == null) return null;
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
