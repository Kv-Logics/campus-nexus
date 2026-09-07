package com.kvlogics.campusnexus.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvlogics.campusnexus.model.RelayNode;
import com.kvlogics.campusnexus.repository.RelayNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class RelayTunnelHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RelayTunnelHandler.class);

    private final RelayNodeRepository relayNodeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map of active WebSocket sessions: sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // Pending requests awaiting phone response: requestId -> CompletableFuture
    private final Map<String, CompletableFuture<ProxyResponsePacket>> pendingRequests = new ConcurrentHashMap<>();

    public RelayTunnelHandler(RelayNodeRepository relayNodeRepository) {
        this.relayNodeRepository = relayNodeRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        URI uri = session.getUri();

        String deviceName = "Campus Mobile Peer";
        String campusIp = session.getRemoteAddress() != null ? session.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        if (uri != null) {
            Map<String, String> queryParams = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
            if (queryParams.containsKey("deviceName")) {
                deviceName = queryParams.get("deviceName");
            }
            if (queryParams.containsKey("campusIp")) {
                campusIp = queryParams.get("campusIp");
            }
        }

        activeSessions.put(sessionId, session);

        Optional<RelayNode> existing = relayNodeRepository.findBySessionId(sessionId);
        RelayNode node = existing.orElseGet(() -> new RelayNode(sessionId, deviceName, campusIp));
        node.setDeviceName(deviceName);
        node.setCampusIp(campusIp);
        node.setStatus("ONLINE");
        node.setConnectedAt(LocalDateTime.now());
        node.setLastPingAt(LocalDateTime.now());
        relayNodeRepository.save(node);

        logger.info("New Campus Relay Node connected: {} [{}] (Session: {})", deviceName, campusIp, sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText();

            if ("PROXY_RESPONSE".equalsIgnoreCase(type)) {
                String requestId = root.path("requestId").asText();
                CompletableFuture<ProxyResponsePacket> future = pendingRequests.remove(requestId);
                if (future != null) {
                    ProxyResponsePacket packet = objectMapper.treeToValue(root, ProxyResponsePacket.class);
                    future.complete(packet);
                }
            } else if ("HEARTBEAT".equalsIgnoreCase(type) || "PONG".equalsIgnoreCase(type)) {
                long latency = root.path("latencyMs").asLong(15);
                relayNodeRepository.findBySessionId(session.getId()).ifPresent(node -> {
                    node.setLastPingAt(LocalDateTime.now());
                    node.setLatencyMs(latency);
                    relayNodeRepository.save(node);
                });
            }
        } catch (Exception e) {
            logger.warn("Failed to parse WebSocket packet from session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);

        relayNodeRepository.findBySessionId(sessionId).ifPresent(node -> {
            node.setStatus("OFFLINE");
            relayNodeRepository.save(node);
        });

        logger.info("Campus Relay Node disconnected (Session: {}, Reason: {})", sessionId, status);
    }

    public CompletableFuture<ProxyResponsePacket> forwardHttpRequest(String targetUrl, String method, String body) {
        if (activeSessions.isEmpty()) {
            CompletableFuture<ProxyResponsePacket> failedFuture = new CompletableFuture<>();
            ProxyResponsePacket err = new ProxyResponsePacket();
            err.setStatus(503);
            err.setError("No active campus relay nodes are currently online. Ask a friend on campus Wi-Fi to tap 'Connect Relay'!");
            failedFuture.complete(err);
            return failedFuture;
        }

        // Select the first active session (or round-robin)
        WebSocketSession session = activeSessions.values().iterator().next();
        String requestId = UUID.randomUUID().toString();

        CompletableFuture<ProxyResponsePacket> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "PROXY_REQUEST",
                    "requestId", requestId,
                    "url", targetUrl,
                    "method", method != null ? method : "GET",
                    "body", body != null ? body : ""
            ));

            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                } else {
                    pendingRequests.remove(requestId);
                    future.completeExceptionally(new IOException("Selected relay session is closed"));
                }
            }
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
