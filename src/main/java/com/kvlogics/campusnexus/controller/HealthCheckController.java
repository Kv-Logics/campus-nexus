package com.kvlogics.campusnexus.controller;

import com.kvlogics.campusnexus.relay.RelayTunnelHandler;
import com.kvlogics.campusnexus.service.FileStagingService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthCheckController {

    private final MongoTemplate mongoTemplate;
    private final RelayTunnelHandler relayTunnelHandler;
    private final FileStagingService fileStagingService;

    public HealthCheckController(MongoTemplate mongoTemplate,
                                 RelayTunnelHandler relayTunnelHandler,
                                 FileStagingService fileStagingService) {
        this.mongoTemplate = mongoTemplate;
        this.relayTunnelHandler = relayTunnelHandler;
        this.fileStagingService = fileStagingService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean dbHealthy = false;
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            dbHealthy = true;
        } catch (Exception ignored) {
        }

        File stagingDir = fileStagingService.getStagingDirectory().toFile();
        long freeSpaceBytes = stagingDir.getUsableSpace();

        return ResponseEntity.ok(Map.of(
                "status", dbHealthy ? "UP" : "DEGRADED",
                "database", dbHealthy ? "CONNECTED (MongoDB Atlas)" : "DISCONNECTED",
                "activeRelayTunnels", relayTunnelHandler.getActiveSessionCount(),
                "stagingFreeSpaceBytes", freeSpaceBytes,
                "timestamp", System.currentTimeMillis()
        ));
    }
}
