package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
    List<FileRecord> findAllByOrderByCreatedAtDesc();
}
