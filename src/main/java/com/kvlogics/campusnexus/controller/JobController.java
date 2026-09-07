package com.kvlogics.campusnexus.controller;

import com.kvlogics.campusnexus.model.TransferJob;
import com.kvlogics.campusnexus.repository.TransferJobRepository;
import com.kvlogics.campusnexus.service.DistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final TransferJobRepository transferJobRepository;
    private final DistributionService distributionService;

    public JobController(TransferJobRepository transferJobRepository,
                         DistributionService distributionService) {
        this.transferJobRepository = transferJobRepository;
        this.distributionService = distributionService;
    }

    @GetMapping
    public List<TransferJob> getJobs(@RequestParam(value = "fileId", required = false) Long fileId) {
        if (fileId != null) {
            return transferJobRepository.findByFileRecordId(fileId);
        }
        return transferJobRepository.findAllByOrderByIdDesc();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryJob(@PathVariable Long id) {
        try {
            TransferJob job = distributionService.retryJobManually(id);
            return ResponseEntity.ok(Map.of("message", "Retry triggered successfully", "job", job));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
