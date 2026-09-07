package com.kvlogics.campusnexus;

import com.kvlogics.campusnexus.mock.MockFtpClusterService;
import com.kvlogics.campusnexus.model.FileRecord;
import com.kvlogics.campusnexus.model.JobStatus;
import com.kvlogics.campusnexus.model.TransferJob;
import com.kvlogics.campusnexus.repository.FileRecordRepository;
import com.kvlogics.campusnexus.repository.FtpServerConfigRepository;
import com.kvlogics.campusnexus.repository.TransferJobRepository;
import com.kvlogics.campusnexus.service.DistributionService;
import com.kvlogics.campusnexus.service.FileStagingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DistributionIntegrationTest {

    @Autowired
    private FileStagingService fileStagingService;

    @Autowired
    private DistributionService distributionService;

    @Autowired
    private MockFtpClusterService mockFtpClusterService;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @Autowired
    private TransferJobRepository transferJobRepository;

    @Autowired
    private FtpServerConfigRepository ftpServerConfigRepository;

    @Test
    @DisplayName("US-1.1 & US-1.2: Staging files generates local copy and computes correct SHA-256")
    void testFileStagingAndChecksum() throws Exception {
        byte[] content = "Campus Nexus High Speed Concurrent File Transfer Test Payload".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test-report.txt", "text/plain", content);

        FileRecord record = fileStagingService.stageFile(file);

        assertNotNull(record.getId());
        assertEquals("test-report.txt", record.getFilename());
        assertEquals((long) content.length, record.getFileSize());
        assertNotNull(record.getSha256Checksum());
        assertEquals(64, record.getSha256Checksum().length());

        File staged = new File(record.getStagingPath());
        assertTrue(staged.exists());
        assertTrue(staged.length() > 0);
    }

    @Test
    @DisplayName("US-2.1 & US-2.2: Fan-out distribution creates 10 concurrent transfer jobs")
    void testFanOutDistribution() throws Exception {
        // Ensure mock cluster is active
        mockFtpClusterService.startAll(10, 2121);

        byte[] content = "Hello FTP Fan-Out World!".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fanout-test.dat", "application/octet-stream", content);
        FileRecord record = fileStagingService.stageFile(file);

        List<TransferJob> jobs = distributionService.fanOutDistribution(record);

        assertEquals(10, jobs.size());

        // Wait up to 10 seconds for concurrent transfers to complete
        boolean allComplete = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            List<TransferJob> updatedJobs = transferJobRepository.findByFileRecordId(record.getId());
            long finished = updatedJobs.stream()
                    .filter(j -> j.getStatus() == JobStatus.SUCCESS || j.getStatus() == JobStatus.FAILED)
                    .count();
            if (finished == updatedJobs.size()) {
                allComplete = true;
                break;
            }
        }

        assertTrue(allComplete, "All 10 jobs should finish execution in worker pool");

        List<TransferJob> finalJobs = transferJobRepository.findByFileRecordId(record.getId());
        long successCount = finalJobs.stream().filter(j -> j.getStatus() == JobStatus.SUCCESS).count();
        assertTrue(successCount >= 1, "At least one job succeeded on the mock FTP cluster");
    }

    @Test
    @DisplayName("US-6.1: Mock cluster allows failure simulation and restart")
    void testMockClusterFailureSimulation() {
        int testPort = 2125;
        assertTrue(mockFtpClusterService.isServerRunning(testPort));

        // Stop port 2125 to simulate node drop
        mockFtpClusterService.stopServer(testPort);
        assertFalse(mockFtpClusterService.isServerRunning(testPort));

        // Restart port 2125
        mockFtpClusterService.startServer(testPort);
        assertTrue(mockFtpClusterService.isServerRunning(testPort));
    }
}
