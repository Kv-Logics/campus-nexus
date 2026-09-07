package com.kvlogics.campusnexus.controller;

import com.kvlogics.campusnexus.model.RelayNode;
import com.kvlogics.campusnexus.relay.ProxyResponsePacket;
import com.kvlogics.campusnexus.relay.RelayTunnelHandler;
import com.kvlogics.campusnexus.repository.RelayNodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/proxy")
public class IntranetProxyController {

    private final RelayTunnelHandler relayTunnelHandler;
    private final RelayNodeRepository relayNodeRepository;

    public IntranetProxyController(RelayTunnelHandler relayTunnelHandler,
                                   RelayNodeRepository relayNodeRepository) {
        this.relayTunnelHandler = relayTunnelHandler;
        this.relayNodeRepository = relayNodeRepository;
    }

    @GetMapping("/nodes")
    public List<RelayNode> getNodes() {
        return relayNodeRepository.findAll();
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "activeTunnels", relayTunnelHandler.getActiveSessionCount(),
                "totalRegisteredNodes", relayNodeRepository.count()
        );
    }

    @GetMapping(value = "/fetch", produces = MediaType.ALL_VALUE)
    public ResponseEntity<?> fetchIntranetUrl(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL parameter is required"));
        }

        try {
            ProxyResponsePacket response = relayTunnelHandler.forwardHttpRequest(url, "GET", null)
                    .get(15, TimeUnit.SECONDS);

            if (response.getError() != null) {
                return ResponseEntity.status(response.getStatus() > 0 ? response.getStatus() : 502)
                        .body(Map.of("error", response.getError()));
            }

            MediaType mediaType = MediaType.TEXT_HTML;
            if (response.getContentType() != null && !response.getContentType().isBlank()) {
                try {
                    mediaType = MediaType.parseMediaType(response.getContentType());
                } catch (Exception ignored) {
                }
            }

            return ResponseEntity.status(response.getStatus())
                    .contentType(mediaType)
                    .body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of("error", "Proxy request timed out or failed: " + e.getMessage()));
        }
    }
}
