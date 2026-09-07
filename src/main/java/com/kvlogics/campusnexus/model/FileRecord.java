package com.kvlogics.campusnexus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "files")
public class FileRecord {

    @Id
    private String id;

    private String filename;
    private String stagingPath;
    private Long fileSize;
    private String sha256Checksum;
    private LocalDateTime createdAt;

    public FileRecord() {
        this.createdAt = LocalDateTime.now();
    }

    public FileRecord(String filename, String stagingPath, Long fileSize, String sha256Checksum) {
        this.filename = filename;
        this.stagingPath = stagingPath;
        this.fileSize = fileSize;
        this.sha256Checksum = sha256Checksum;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getStagingPath() {
        return stagingPath;
    }

    public void setStagingPath(String stagingPath) {
        this.stagingPath = stagingPath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
