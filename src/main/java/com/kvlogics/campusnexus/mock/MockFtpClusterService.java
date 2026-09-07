package com.kvlogics.campusnexus.mock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockFtpClusterService {

    private static final Logger logger = LoggerFactory.getLogger(MockFtpClusterService.class);

    private final Map<Integer, FakeFtpServer> activeServers = new ConcurrentHashMap<>();
    private final boolean autoStartCluster;

    public MockFtpClusterService(@Value("${app.mock-ftp.auto-start:true}") boolean autoStartCluster) {
        this.autoStartCluster = autoStartCluster;
    }

    @PostConstruct
    public void init() {
        if (autoStartCluster) {
            startAll(10, 2121);
        }
    }

    public synchronized void startAll(int count, int startPort) {
        logger.info("Starting {} embedded Mock FTP servers starting at port {}...", count, startPort);
        for (int i = 0; i < count; i++) {
            int port = startPort + i;
            startServer(port);
        }
    }

    public synchronized void startServer(int port) {
        if (activeServers.containsKey(port)) {
            logger.warn("Mock FTP server on port {} already running.", port);
            return;
        }

        try {
            FakeFtpServer server = new FakeFtpServer();
            server.setServerControlPort(port);

            // In-memory virtual filesystem
            FileSystem fileSystem = new UnixFakeFileSystem();
            fileSystem.add(new DirectoryEntry("/data"));
            fileSystem.add(new DirectoryEntry("/data/incoming"));
            server.setFileSystem(fileSystem);

            // Default user account: ftpuser / secret123
            UserAccount userAccount = new UserAccount("ftpuser", "secret123", "/data/incoming");
            server.addUserAccount(userAccount);

            server.start();
            activeServers.put(port, server);
            logger.info("Mock FTP Server started on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start Mock FTP Server on port {}: {}", port, e.getMessage());
        }
    }

    public synchronized void stopServer(int port) {
        FakeFtpServer server = activeServers.remove(port);
        if (server != null) {
            try {
                server.stop();
                logger.info("Stopped Mock FTP Server on port {}", port);
            } catch (Exception e) {
                logger.warn("Error stopping Mock FTP Server on port {}: {}", port, e.getMessage());
            }
        }
    }

    public synchronized void toggleServer(int port) {
        if (isServerRunning(port)) {
            stopServer(port);
        } else {
            startServer(port);
        }
    }

    public boolean isServerRunning(int port) {
        return activeServers.containsKey(port);
    }

    public Map<Integer, Boolean> getClusterStatus() {
        Map<Integer, Boolean> status = new ConcurrentHashMap<>();
        for (int port = 2121; port <= 2130; port++) {
            status.put(port, activeServers.containsKey(port));
        }
        return status;
    }

    @PreDestroy
    public synchronized void stopAll() {
        logger.info("Shutting down all Mock FTP servers...");
        for (Integer port : activeServers.keySet()) {
            stopServer(port);
        }
        activeServers.clear();
    }
}
