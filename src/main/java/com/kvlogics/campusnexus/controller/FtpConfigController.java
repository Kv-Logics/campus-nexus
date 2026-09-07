package com.kvlogics.campusnexus.controller;

import com.kvlogics.campusnexus.mock.MockFtpClusterService;
import com.kvlogics.campusnexus.model.FtpServerConfig;
import com.kvlogics.campusnexus.repository.FtpServerConfigRepository;
import com.kvlogics.campusnexus.service.FtpClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ftp")
public class FtpConfigController {

    private final FtpServerConfigRepository ftpServerConfigRepository;
    private final FtpClientService ftpClientService;
    private final MockFtpClusterService mockFtpClusterService;

    public FtpConfigController(FtpServerConfigRepository ftpServerConfigRepository,
                               FtpClientService ftpClientService,
                               MockFtpClusterService mockFtpClusterService) {
        this.ftpServerConfigRepository = ftpServerConfigRepository;
        this.ftpClientService = ftpClientService;
        this.mockFtpClusterService = mockFtpClusterService;
    }

    @GetMapping("/servers")
    public List<FtpServerConfig> listServers() {
        return ftpServerConfigRepository.findAll();
    }

    @PostMapping("/servers")
    public FtpServerConfig saveServer(@RequestBody FtpServerConfig config) {
        return ftpServerConfigRepository.save(config);
    }

    @PutMapping("/servers/{id}")
    public ResponseEntity<?> updateServer(@PathVariable Long id, @RequestBody FtpServerConfig updatedConfig) {
        return ftpServerConfigRepository.findById(id).map(config -> {
            config.setName(updatedConfig.getName());
            config.setHost(updatedConfig.getHost());
            config.setPort(updatedConfig.getPort());
            config.setUsername(updatedConfig.getUsername());
            if (updatedConfig.getPassword() != null && !updatedConfig.getPassword().isBlank()) {
                config.setPassword(updatedConfig.getPassword());
            }
            config.setRemoteDir(updatedConfig.getRemoteDir());
            config.setProtocol(updatedConfig.getProtocol());
            config.setEnabled(updatedConfig.isEnabled());
            ftpServerConfigRepository.save(config);
            return ResponseEntity.ok(config);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/servers/{id}/toggle")
    public ResponseEntity<?> toggleServer(@PathVariable Long id) {
        return ftpServerConfigRepository.findById(id).map(config -> {
            config.setEnabled(!config.isEnabled());
            ftpServerConfigRepository.save(config);
            return ResponseEntity.ok(config);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/servers/{id}/test")
    public ResponseEntity<?> testServer(@PathVariable Long id) {
        return ftpServerConfigRepository.findById(id).map(config -> {
            boolean success = ftpClientService.testConnection(config);
            return ResponseEntity.ok(Map.of(
                    "serverId", id,
                    "serverName", config.getName(),
                    "connectionSuccess", success,
                    "message", success ? "Successfully connected and authenticated" : "Connection or authentication failed"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mock-cluster")
    public Map<Integer, Boolean> getMockClusterStatus() {
        return mockFtpClusterService.getClusterStatus();
    }

    @PostMapping("/mock-cluster/{port}/toggle")
    public ResponseEntity<?> toggleMockServer(@PathVariable int port) {
        mockFtpClusterService.toggleServer(port);
        boolean isRunning = mockFtpClusterService.isServerRunning(port);
        return ResponseEntity.ok(Map.of(
                "port", port,
                "running", isRunning,
                "message", isRunning ? "Mock FTP Server started" : "Mock FTP Server stopped (simulating failure)"
        ));
    }
}
