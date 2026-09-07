package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.FileRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRecordRepository extends MongoRepository<FileRecord, String> {
    List<FileRecord> findAllByOrderByCreatedAtDesc();
}
