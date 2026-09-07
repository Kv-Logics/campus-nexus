package com.kvlogics.campusnexus.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "files")
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String stagingPath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 64)
    private String sha256Checksum;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public FileRecord() {
    }

    public FileRecord(String filename, String stagingPath, Long fileSize, String sha256Checksum) {
        this.filename = filename;
        this.stagingPath = stagingPath;
        this.fileSize = fileSize;
        this.sha256Checksum = sha256Checksum;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
