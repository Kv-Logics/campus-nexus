package com.kvlogics.campusnexus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "transfer_jobs")
public class TransferJob {

    @Id
    private String id;

    @DBRef
    private FileRecord fileRecord;

    @DBRef
    private FtpServerConfig ftpServerConfig;

    private JobStatus status;
    private int attemptCount = 0;
    private int maxAttempts = 3;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public TransferJob() {
        this.status = JobStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = 3;
    }

    public TransferJob(FileRecord fileRecord, FtpServerConfig ftpServerConfig) {
        this.fileRecord = fileRecord;
        this.ftpServerConfig = ftpServerConfig;
        this.status = JobStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = 3;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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
