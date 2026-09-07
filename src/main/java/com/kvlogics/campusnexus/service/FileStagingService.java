package com.kvlogics.campusnexus.service;

import com.kvlogics.campusnexus.model.FileRecord;
import com.kvlogics.campusnexus.repository.FileRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileStagingService {

    private static final Logger logger = LoggerFactory.getLogger(FileStagingService.class);

    private final Path stagingDirectory;
    private final FileRecordRepository fileRecordRepository;

    public FileStagingService(@Value("${app.storage.staging-dir:./staging}") String stagingDir,
                              FileRecordRepository fileRecordRepository) throws IOException {
        this.stagingDirectory = Paths.get(stagingDir).toAbsolutePath().normalize();
        this.fileRecordRepository = fileRecordRepository;
        Files.createDirectories(this.stagingDirectory);
        logger.info("Staging directory initialized at: {}", this.stagingDirectory);
    }

    public FileRecord stageFile(MultipartFile multipartFile) throws IOException, NoSuchAlgorithmException {
        String originalFilename = multipartFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "file_" + System.currentTimeMillis();
        }

        // Sanitize and create unique staging path to prevent overwrites
        String sanitizedName = Paths.get(originalFilename).getFileName().toString();
        String uniqueName = UUID.randomUUID().toString() + "_" + sanitizedName;
        Path targetPath = stagingDirectory.resolve(uniqueName);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = multipartFile.getInputStream();
             DigestInputStream digestStream = new DigestInputStream(in, digest);
             FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {

            byte[] buffer = new byte[65536]; // 64KB buffer for high I/O performance
            int read;
            while ((read = digestStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        long fileSize = Files.size(targetPath);
        String checksum = HexFormat.of().formatHex(digest.digest());

        logger.info("Staged file '{}' (size: {} bytes, SHA-256: {})", sanitizedName, fileSize, checksum);

        FileRecord record = new FileRecord(sanitizedName, targetPath.toString(), fileSize, checksum);
        return fileRecordRepository.save(record);
    }

    public File getStagedFile(String stagingPath) {
        return new File(stagingPath);
    }

    public boolean deleteStagedFile(String stagingPath) {
        if (stagingPath == null || stagingPath.isBlank()) return false;
        try {
            Path path = Paths.get(stagingPath);
            boolean deleted = Files.deleteIfExists(path);
            logger.info("Deleted staged file '{}': {}", stagingPath, deleted);
            return deleted;
        } catch (IOException e) {
            logger.warn("Failed to delete staged file '{}': {}", stagingPath, e.getMessage());
            return false;
        }
    }

    public Path getStagingDirectory() {
        return stagingDirectory;
    }
}
