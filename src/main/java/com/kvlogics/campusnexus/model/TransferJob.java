package com.kvlogics.campusnexus.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_jobs")
public class TransferJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id", nullable = false)
    private FileRecord fileRecord;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "ftp_server_id", nullable = false)
    private FtpServerConfig ftpServerConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int attemptCount = 0;

    @Column(nullable = false)
    private int maxAttempts = 3;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(length = 2048)
    private String errorMessage;

    public TransferJob() {
    }

    public TransferJob(FileRecord fileRecord, FtpServerConfig ftpServerConfig) {
        this.fileRecord = fileRecord;
        this.ftpServerConfig = ftpServerConfig;
        this.status = JobStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = 3;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileRecord getFileRecord() {
        return fileRecord;
    }

    public void setFileRecord(FileRecord fileRecord) {
        this.fileRecord = fileRecord;
    }

    public FtpServerConfig getFtpServerConfig() {
        return ftpServerConfig;
    }

    public void setFtpServerConfig(FtpServerConfig ftpServerConfig) {
        this.ftpServerConfig = ftpServerConfig;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
