package com.kvlogics.campusnexus.worker;

import com.kvlogics.campusnexus.model.FtpServerConfig;
import com.kvlogics.campusnexus.model.JobStatus;
import com.kvlogics.campusnexus.model.TransferJob;
import com.kvlogics.campusnexus.repository.TransferJobRepository;
import com.kvlogics.campusnexus.service.FileStagingService;
import com.kvlogics.campusnexus.service.FtpClientService;
import com.kvlogics.campusnexus.service.RetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;

public class TransferWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TransferWorker.class);

    private final Long jobId;
    private final TransferJobRepository jobRepository;
    private final FtpClientService ftpClientService;
    private final FileStagingService stagingService;
    private final RetryService retryService;

    public TransferWorker(Long jobId,
                          TransferJobRepository jobRepository,
                          FtpClientService ftpClientService,
                          FileStagingService stagingService,
                          RetryService retryService) {
        this.jobId = jobId;
        this.jobRepository = jobRepository;
        this.ftpClientService = ftpClientService;
        this.stagingService = stagingService;
        this.retryService = retryService;
    }

    @Override
    public void run() {
        TransferJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            logger.error("Job with ID {} not found in database.", jobId);
            return;
        }

        job.setStatus(JobStatus.UPLOADING);
        job.setStartedAt(LocalDateTime.now());
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setErrorMessage(null);
        job = jobRepository.save(job);

        FtpServerConfig ftpConfig = job.getFtpServerConfig();
        File stagedFile = stagingService.getStagedFile(job.getFileRecord().getStagingPath());

        logger.info("[Job #{}] Attempt {}/{} starting upload to '{}'...",
                job.getId(), job.getAttemptCount(), job.getMaxAttempts(), ftpConfig.getName());

        try {
            if (!stagedFile.exists() || !stagedFile.canRead()) {
                throw new IllegalStateException("Staged file not accessible at: " + stagedFile.getAbsolutePath());
            }

            ftpClientService.uploadFile(ftpConfig, stagedFile);

            job.setStatus(JobStatus.SUCCESS);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            jobRepository.save(job);
            logger.info("[Job #{}] Successfully transferred to '{}'", job.getId(), ftpConfig.getName());

        } catch (Exception e) {
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.warn("[Job #{}] Failed transfer to '{}': {}", job.getId(), ftpConfig.getName(), error);

            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(error);
            jobRepository.save(job);

            if (job.getAttemptCount() < job.getMaxAttempts()) {
                retryService.scheduleRetry(job.getId(), job.getAttemptCount());
            } else {
                logger.error("[Job #{}] Exceeded maximum retry attempts ({}). Marked as permanently FAILED.",
                        job.getId(), job.getMaxAttempts());
            }
        }
    }
}
