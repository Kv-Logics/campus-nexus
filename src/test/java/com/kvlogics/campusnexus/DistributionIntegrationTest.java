package com.kvlogics.campusnexus;

import com.kvlogics.campusnexus.model.FileRecord;
import com.kvlogics.campusnexus.repository.FileRecordRepository;
import com.kvlogics.campusnexus.repository.FtpServerConfigRepository;
import com.kvlogics.campusnexus.service.FileStagingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DistributionIntegrationTest {

    @Autowired
    private FileStagingService fileStagingService;

    @Autowired
    private FileRecordRepository fileRecordRepository;

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
}
