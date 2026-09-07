package com.kvlogics.campusnexus.controller;

import com.kvlogics.campusnexus.model.FileRecord;
import com.kvlogics.campusnexus.model.TransferJob;
import com.kvlogics.campusnexus.repository.FileRecordRepository;
import com.kvlogics.campusnexus.repository.TransferJobRepository;
import com.kvlogics.campusnexus.service.DistributionService;
import com.kvlogics.campusnexus.service.FileStagingService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileStagingService fileStagingService;
    private final DistributionService distributionService;
    private final FileRecordRepository fileRecordRepository;
    private final TransferJobRepository transferJobRepository;

    public FileUploadController(FileStagingService fileStagingService,
                                DistributionService distributionService,
                                FileRecordRepository fileRecordRepository,
                                TransferJobRepository transferJobRepository) {
        this.fileStagingService = fileStagingService;
        this.distributionService = distributionService;
        this.fileRecordRepository = fileRecordRepository;
        this.transferJobRepository = transferJobRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "autoDistribute", defaultValue = "false") boolean autoDistribute) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            // 1. Stage file locally & compute SHA-256
            FileRecord record = fileStagingService.stageFile(file);

            if (autoDistribute) {
                // Fan-out distribution across all active FTP servers immediately
                List<TransferJob> jobs = distributionService.fanOutDistribution(record);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "file", record,
                        "jobsCreated", jobs.size(),
                        "jobs", jobs,
                        "staged", true
                ));
            }

            // Return staged file metadata for target selection step
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "file", record,
                    "jobsCreated", 0,
                    "jobs", Collections.emptyList(),
                    "staged", true,
                    "message", "File staged and cryptographic checksum calculated. Please select destination FTP servers to dispatch."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to stage file: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/distribute")
    public ResponseEntity<?> distributeFile(@PathVariable String id,
                                           @RequestBody(required = false) Map<String, Object> payload) {
        return fileRecordRepository.findById(id).map(record -> {
            @SuppressWarnings("unchecked")
            List<String> serverIds = payload != null && payload.containsKey("serverIds")
                    ? (List<String>) payload.get("serverIds")
                    : null;

            List<TransferJob> jobs = distributionService.fanOutDistributionToTargets(record, serverIds);
            return ResponseEntity.ok(Map.of(
                    "file", record,
                    "jobsCreated", jobs.size(),
                    "jobs", jobs,
                    "message", "Successfully dispatched to " + jobs.size() + " FTP server(s)"
            ));
        }).orElse(ResponseEntity.notFound().build());
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

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadFile(@PathVariable String id) {
        return fileRecordRepository.findById(id).map(record -> {
            File file = fileStagingService.getStagedFile(record.getStagingPath());
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body((Object) Map.of("error", "Staged file not found on disk"));
            }

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body((Object) resource);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id) {
        return fileRecordRepository.findById(id).map(record -> {
            // 1. Delete physical staged file from disk
            fileStagingService.deleteStagedFile(record.getStagingPath());

            // 2. Delete associated transfer jobs from MongoDB
            List<TransferJob> jobs = transferJobRepository.findByFileRecordId(id);
            if (!jobs.isEmpty()) {
                transferJobRepository.deleteAll(jobs);
            }

            // 3. Delete FileRecord from MongoDB
            fileRecordRepository.deleteById(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Staged task, file, and " + jobs.size() + " associated transfer jobs deleted successfully"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
