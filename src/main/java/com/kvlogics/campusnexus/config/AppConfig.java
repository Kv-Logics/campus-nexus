package com.kvlogics.campusnexus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AppConfig {

    @Value("${app.storage.worker-threads:10}")
    private int workerThreads;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService ftpWorkerPool() {
        return Executors.newFixedThreadPool(workerThreads);
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService retryScheduler() {
        return Executors.newScheduledThreadPool(4);
    }
}
