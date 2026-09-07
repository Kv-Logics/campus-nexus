package com.kvlogics.campusnexus.config;

import com.kvlogics.campusnexus.model.FtpServerConfig;
import com.kvlogics.campusnexus.repository.FtpServerConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final FtpServerConfigRepository ftpServerConfigRepository;

    public DataInitializer(FtpServerConfigRepository ftpServerConfigRepository) {
        this.ftpServerConfigRepository = ftpServerConfigRepository;
    }

    @Override
    public void run(String... args) {
        if (ftpServerConfigRepository.count() == 0) {
            logger.info("Seeding 10 default FTP server destinations (ports 2121-2130)...");
            for (int i = 1; i <= 10; i++) {
                String name = String.format("FTP-%02d", i);
                int port = 2120 + i;
                FtpServerConfig config = new FtpServerConfig(
                        name,
                        "127.0.0.1",
                        port,
                        "ftpuser",
                        "secret123",
                        "/data/incoming",
                        "FTP",
                        true
                );
                ftpServerConfigRepository.save(config);
            }
            logger.info("Successfully seeded 10 FTP servers.");
        }
    }
}
