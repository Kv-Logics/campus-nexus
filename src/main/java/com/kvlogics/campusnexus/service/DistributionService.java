package com.kvlogics.campusnexus.service;

import com.kvlogics.campusnexus.model.FileRecord;
import com.kvlogics.campusnexus.model.FtpServerConfig;
import com.kvlogics.campusnexus.model.JobStatus;
import com.kvlogics.campusnexus.model.TransferJob;
import com.kvlogics.campusnexus.repository.FtpServerConfigRepository;
import com.kvlogics.campusnexus.repository.TransferJobRepository;
import com.kvlogics.campusnexus.worker.TransferWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class DistributionService {

    private static final Logger logger = LoggerFactory.getLogger(DistributionService.class);

    private final FtpServerConfigRepository ftpServerConfigRepository;
    private final TransferJobRepository transferJobRepository;
    private final FtpClientService ftpClientService;
    private final FileStagingService stagingService;
    private final RetryService retryService;
    private final ExecutorService ftpWorkerPool;

    public DistributionService(FtpServerConfigRepository ftpServerConfigRepository,
                               TransferJobRepository transferJobRepository,
                               FtpClientService ftpClientService,
                               FileStagingService stagingService,
                               RetryService retryService,
                               ExecutorService ftpWorkerPool) {
        this.ftpServerConfigRepository = ftpServerConfigRepository;
        this.transferJobRepository = transferJobRepository;
        this.ftpClientService = ftpClientService;
        this.stagingService = stagingService;
        this.retryService = retryService;
        this.ftpWorkerPool = ftpWorkerPool;
    }

    @Transactional
    public List<TransferJob> fanOutDistribution(FileRecord fileRecord) {
        List<FtpServerConfig> servers = ftpServerConfigRepository.findByEnabledTrue();
        logger.info("Fanning out distribution of file '{}' (ID: {}) to {} enabled FTP servers...",
                fileRecord.getFilename(), fileRecord.getId(), servers.size());

        List<TransferJob> jobs = new ArrayList<>();
        for (FtpServerConfig server : servers) {
            TransferJob job = new TransferJob(fileRecord, server);
            job = transferJobRepository.save(job);
            jobs.add(job);
        }

        // Dispatch all jobs concurrently into the bounded worker pool
        for (TransferJob job : jobs) {
            executeJob(job.getId());
        }

        return jobs;
    }

    public void executeJob(Long jobId) {
        TransferWorker worker = new TransferWorker(
                jobId,
                transferJobRepository,
                ftpClientService,
                stagingService,
                retryService
        );
        ftpWorkerPool.submit(worker);
    }

    @Transactional
    public TransferJob retryJobManually(Long jobId) {
        TransferJob job = transferJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer job not found with ID: " + jobId));

        logger.info("Manual retry requested for Job #{} (Target: {})", jobId, job.getFtpServerConfig().getName());
        job.setStatus(JobStatus.PENDING);
        job.setErrorMessage(null);
        job = transferJobRepository.save(job);

        executeJob(jobId);
        return job;
    }
}
