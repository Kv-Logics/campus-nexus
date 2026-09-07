package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.JobStatus;
import com.kvlogics.campusnexus.model.TransferJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferJobRepository extends MongoRepository<TransferJob, String> {
    List<TransferJob> findByFileRecordId(String fileRecordId);
    List<TransferJob> findByStatus(JobStatus status);
    List<TransferJob> findAllByOrderByIdDesc();
}
