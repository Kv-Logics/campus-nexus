package com.kvlogics.campusnexus.service;

import com.kvlogics.campusnexus.model.FtpServerConfig;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Service
public class FtpClientService {

    private static final Logger logger = LoggerFactory.getLogger(FtpClientService.class);

    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int DATA_TIMEOUT_MS = 30000;

    public void uploadFile(FtpServerConfig config, File localFile) throws IOException {
        FTPClient ftpClient = createClient(config);

        try {
            ftpClient.setConnectTimeout(CONNECT_TIMEOUT_MS);
            ftpClient.setDefaultTimeout(CONNECT_TIMEOUT_MS);
            ftpClient.setDataTimeout(DATA_TIMEOUT_MS);

            logger.info("Connecting to FTP server [{}] at {}:{}", config.getName(), config.getHost(), config.getPort());
            ftpClient.connect(config.getHost(), config.getPort());

            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                throw new IOException("FTP server refused connection with reply code: " + replyCode);
            }

            boolean loginSuccess = ftpClient.login(config.getUsername(), config.getPassword());
            if (!loginSuccess) {
                throw new IOException("FTP login failed for user '" + config.getUsername() + "'");
            }

            // Enter local passive mode to work behind NATs/firewalls
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setBufferSize(1024 * 64); // 64KB network buffer

            // Navigate or create remote directory if needed
            String remoteDir = config.getRemoteDir();
            if (remoteDir != null && !remoteDir.isBlank()) {
                makeDirectories(ftpClient, remoteDir);
                if (!ftpClient.changeWorkingDirectory(remoteDir)) {
                    throw new IOException("Failed to change working directory to: " + remoteDir);
                }
            }

            String remoteFilename = localFile.getName();
            logger.info("Uploading '{}' to [{}] in remote directory '{}'...", remoteFilename, config.getName(), remoteDir);

            try (FileInputStream fis = new FileInputStream(localFile)) {
                boolean done = ftpClient.storeFile(remoteFilename, fis);
                if (!done) {
                    throw new IOException("FTP storeFile failed with reply: " + ftpClient.getReplyString());
                }
            }

            logger.info("Successfully uploaded '{}' to [{}]", remoteFilename, config.getName());

        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (IOException e) {
                    logger.warn("Error disconnecting from FTP [{}]", config.getName(), e);
                }
            }
        }
    }

    public boolean testConnection(FtpServerConfig config) {
        FTPClient ftpClient = createClient(config);
        try {
            ftpClient.setConnectTimeout(CONNECT_TIMEOUT_MS);
            ftpClient.connect(config.getHost(), config.getPort());
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                return false;
            }
            boolean login = ftpClient.login(config.getUsername(), config.getPassword());
            ftpClient.logout();
            return login;
        } catch (Exception e) {
            logger.warn("Connection test failed for [{}]: {}", config.getName(), e.getMessage());
            return false;
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private FTPClient createClient(FtpServerConfig config) {
        if ("FTPS".equalsIgnoreCase(config.getProtocol())) {
            return new FTPSClient();
        }
        return new FTPClient();
    }

    private void makeDirectories(FTPClient ftpClient, String path) throws IOException {
        String[] parts = path.split("[/\\\\]");
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!ftpClient.changeWorkingDirectory(part)) {
                    if (ftpClient.makeDirectory(part)) {
                        ftpClient.changeWorkingDirectory(part);
                    }
                }
            }
        }
    }
}
