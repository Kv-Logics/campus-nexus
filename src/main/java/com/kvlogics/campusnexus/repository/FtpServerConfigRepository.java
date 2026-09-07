package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.FtpServerConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FtpServerConfigRepository extends MongoRepository<FtpServerConfig, String> {
    List<FtpServerConfig> findByEnabledTrue();
    Optional<FtpServerConfig> findByName(String name);
}
