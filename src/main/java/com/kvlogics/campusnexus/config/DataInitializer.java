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
        if (ftpServerConfigRepository.count() == 0) {
            File configFile = new File("ftp-servers.json");
            if (configFile.exists()) {
                try {
                    logger.info("Loading FTP destinations from 'ftp-servers.json'...");
                    List<FtpServerConfig> servers = objectMapper.readValue(configFile, new TypeReference<List<FtpServerConfig>>() {});
                    ftpServerConfigRepository.saveAll(servers);
                    logger.info("Successfully loaded {} FTP server configurations from ftp-servers.json", servers.size());
                    return;
                } catch (Exception e) {
                    logger.warn("Could not parse ftp-servers.json, falling back to default seed: {}", e.getMessage());
                }
            }

            logger.info("Seeding 10 default FTP server destinations (ports 2121-2130)...");
            for (int i = 1; i <= 10; i++) {
                String name = String.format("FTP-%02d", i);
                int port = 2120 + i;
                FtpServerConfig config = new FtpServerConfig(
                        name,
                        "127.0.0.1",
                        21,
                        "ftpuser",
                        "",
                        "/",
                        "FTP",
                        false
                );
                ftpServerConfigRepository.save(config);
            }
            logger.info("Successfully seeded 10 FTP servers.");
        }
    }
}
