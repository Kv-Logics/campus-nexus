package com.kvlogics.campusnexus.controller;

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

    public FtpConfigController(FtpServerConfigRepository ftpServerConfigRepository,
                               FtpClientService ftpClientService) {
        this.ftpServerConfigRepository = ftpServerConfigRepository;
        this.ftpClientService = ftpClientService;
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
    public ResponseEntity<?> updateServer(@PathVariable String id, @RequestBody FtpServerConfig updatedConfig) {
        return ftpServerConfigRepository.findById(id).map(config -> {
            config.setName(updatedConfig.getName());
            config.setHost(updatedConfig.getHost());
            config.setPort(updatedConfig.getPort() > 0 ? updatedConfig.getPort() : 21);
            config.setUsername(updatedConfig.getUsername());
            if (updatedConfig.getPassword() != null && !updatedConfig.getPassword().isBlank()) {
                config.setPassword(updatedConfig.getPassword());
            }
            config.setRemoteDir(updatedConfig.getRemoteDir() != null && !updatedConfig.getRemoteDir().isBlank() ? updatedConfig.getRemoteDir() : "/");
            config.setProtocol(updatedConfig.getProtocol() != null ? updatedConfig.getProtocol() : "FTP");
            config.setEnabled(updatedConfig.isEnabled());
            ftpServerConfigRepository.save(config);
            return ResponseEntity.ok(config);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/servers/disable-all")
    public List<FtpServerConfig> disableAllServers() {
        List<FtpServerConfig> servers = ftpServerConfigRepository.findAll();
        servers.forEach(s -> s.setEnabled(false));
        return ftpServerConfigRepository.saveAll(servers);
    }

    @PostMapping("/servers/enable-all")
    public List<FtpServerConfig> enableAllServers() {
        List<FtpServerConfig> servers = ftpServerConfigRepository.findAll();
        servers.forEach(s -> s.setEnabled(true));
        return ftpServerConfigRepository.saveAll(servers);
    }

    @PostMapping("/servers/{id}/toggle")
    public ResponseEntity<?> toggleServer(@PathVariable String id) {
        return ftpServerConfigRepository.findById(id).map(config -> {
            config.setEnabled(!config.isEnabled());
            ftpServerConfigRepository.save(config);
            return ResponseEntity.ok(config);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/servers/{id}/test")
    public ResponseEntity<?> testServer(@PathVariable String id) {
        return ftpServerConfigRepository.findById(id).map(config -> {
            if (config.getHost() == null || config.getHost().isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "serverId", id,
                        "serverName", config.getName(),
                        "connectionSuccess", false,
                        "message", "Host IP address is not configured yet. Click 'Edit' to enter host details."
                ));
            }

            boolean success = ftpClientService.testConnection(config);
            return ResponseEntity.ok(Map.of(
                    "serverId", id,
                    "serverName", config.getName(),
                    "connectionSuccess", success,
                    "message", success ? "Successfully connected and authenticated on port " + config.getPort() : "Connection failed to " + config.getHost() + ":" + config.getPort()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
