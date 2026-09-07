package com.kvlogics.campusnexus.controller;

import com.kvlogics.campusnexus.model.FileRecord;
import com.kvlogics.campusnexus.model.TransferJob;
import com.kvlogics.campusnexus.repository.FileRecordRepository;
import com.kvlogics.campusnexus.service.DistributionService;
import com.kvlogics.campusnexus.service.FileStagingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileStagingService fileStagingService;
    private final DistributionService distributionService;
    private final FileRecordRepository fileRecordRepository;

    public FileUploadController(FileStagingService fileStagingService,
                                DistributionService distributionService,
                                FileRecordRepository fileRecordRepository) {
        this.fileStagingService = fileStagingService;
        this.distributionService = distributionService;
        this.fileRecordRepository = fileRecordRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            // 1. Stage file locally & compute SHA-256
            FileRecord record = fileStagingService.stageFile(file);

            // 2. Fan-out distribution across all active FTP servers
            List<TransferJob> jobs = distributionService.fanOutDistribution(record);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "file", record,
                    "jobsCreated", jobs.size(),
                    "jobs", jobs
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to stage and distribute file: " + e.getMessage()));
        }
    }

    @GetMapping
    public List<FileRecord> listFiles() {
        return fileRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFile(@PathVariable String id) {
        return fileRecordRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
