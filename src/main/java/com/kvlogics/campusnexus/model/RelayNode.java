package com.kvlogics.campusnexus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "relay_nodes")
public class RelayNode {

    @Id
    private String id;

    private String sessionId;
    private String deviceName;
    private String campusIp;
    private String status; // ONLINE, BUSY, OFFLINE
    private long latencyMs;
    private LocalDateTime connectedAt;
    private LocalDateTime lastPingAt;

    public RelayNode() {
        this.connectedAt = LocalDateTime.now();
        this.lastPingAt = LocalDateTime.now();
    }

    public RelayNode(String sessionId, String deviceName, String campusIp) {
        this.sessionId = sessionId;
        this.deviceName = deviceName;
        this.campusIp = campusIp;
        this.status = "ONLINE";
        this.connectedAt = LocalDateTime.now();
        this.lastPingAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCampusIp() {
        return campusIp;
    }

    public void setCampusIp(String campusIp) {
        this.campusIp = campusIp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }

    public LocalDateTime getLastPingAt() {
        return lastPingAt;
    }

    public void setLastPingAt(LocalDateTime lastPingAt) {
        this.lastPingAt = lastPingAt;
    }
}
