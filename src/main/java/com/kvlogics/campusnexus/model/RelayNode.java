package com.kvlogics.campusnexus.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "relay_nodes")
public class RelayNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false)
    private String deviceName;

    private String campusIp;

    @Column(nullable = false)
    private String status; // ONLINE, BUSY, OFFLINE

    private long latencyMs;

    @Column(nullable = false)
    private LocalDateTime connectedAt;

    private LocalDateTime lastPingAt;

    public RelayNode() {
    }

    public RelayNode(String sessionId, String deviceName, String campusIp) {
        this.sessionId = sessionId;
        this.deviceName = deviceName;
        this.campusIp = campusIp;
        this.status = "ONLINE";
        this.connectedAt = LocalDateTime.now();
        this.lastPingAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
