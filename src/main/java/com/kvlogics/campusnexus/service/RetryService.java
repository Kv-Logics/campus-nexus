package com.kvlogics.campusnexus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);

    private final ScheduledExecutorService retryScheduler;
    private final ObjectProvider<DistributionService> distributionServiceProvider;

    public RetryService(ScheduledExecutorService retryScheduler,
                        @Lazy ObjectProvider<DistributionService> distributionServiceProvider) {
        this.retryScheduler = retryScheduler;
        this.distributionServiceProvider = distributionServiceProvider;
    }

    public void scheduleRetry(String jobId, int completedAttemptCount) {
        // Attempt 1 -> wait 5s, Attempt 2 -> wait 30s
        long delaySeconds = (completedAttemptCount == 1) ? 5 : 30;

        logger.info("[Job #{}] Scheduling automatic retry #{} in {} seconds...",
                jobId, completedAttemptCount + 1, delaySeconds);

        retryScheduler.schedule(() -> {
            try {
                DistributionService distributionService = distributionServiceProvider.getIfAvailable();
                if (distributionService != null) {
                    logger.info("[Job #{}] Executing scheduled retry attempt #{}", jobId, completedAttemptCount + 1);
                    distributionService.executeJob(jobId);
                }
            } catch (Exception e) {
                logger.error("[Job #{}] Error during scheduled retry execution: {}", jobId, e.getMessage(), e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
}
