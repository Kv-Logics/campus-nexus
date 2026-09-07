package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.JobStatus;
import com.kvlogics.campusnexus.model.TransferJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferJobRepository extends JpaRepository<TransferJob, Long> {
    List<TransferJob> findByFileRecordId(Long fileRecordId);
    List<TransferJob> findByStatus(JobStatus status);
    List<TransferJob> findAllByOrderByIdDesc();
}
