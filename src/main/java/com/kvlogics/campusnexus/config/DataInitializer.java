package com.kvlogics.campusnexus.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvlogics.campusnexus.model.FtpServerConfig;
import com.kvlogics.campusnexus.repository.FtpServerConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final FtpServerConfigRepository ftpServerConfigRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataInitializer(FtpServerConfigRepository ftpServerConfigRepository) {
        this.ftpServerConfigRepository = ftpServerConfigRepository;
    }

    @Override
    public void run(String... args) {
        List<FtpServerConfig> existing = ftpServerConfigRepository.findAll();

        // Check if existing data contains mock data (ports 2121-2130 or user 'ftpuser')
        boolean hasMockData = existing.stream().anyMatch(s -> s.getPort() > 2100 || "ftpuser".equalsIgnoreCase(s.getUsername()));

        if (existing.isEmpty() || hasMockData) {
            logger.info("Purging old mock data and initializing 10 clean production FTP templates (Port 21, Disabled)...");
            ftpServerConfigRepository.deleteAll();

            File configFile = new File("ftp-servers.json");
            if (configFile.exists()) {
                try {
                    List<FtpServerConfig> servers = objectMapper.readValue(configFile, new TypeReference<List<FtpServerConfig>>() {});
                    ftpServerConfigRepository.saveAll(servers);
                    logger.info("Successfully initialized {} production FTP server configurations from ftp-servers.json", servers.size());
                    return;
                } catch (Exception e) {
                    logger.warn("Could not parse ftp-servers.json: {}", e.getMessage());
                }
            }

            // Fallback: 10 production entries (all port 21, all disabled)
            List<FtpServerConfig> defaultProdServers = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String name = String.format("FTP-%02d", i);
                defaultProdServers.add(new FtpServerConfig(
                        name,
                        "",
                        21,
                        "",
                        "",
                        "/",
                        "FTP",
                        false
                ));
            }
            ftpServerConfigRepository.saveAll(defaultProdServers);
            logger.info("Successfully seeded 10 production FTP servers (all disabled).");
        }
    }
}
