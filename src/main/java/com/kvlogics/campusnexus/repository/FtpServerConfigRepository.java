package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.FtpServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FtpServerConfigRepository extends JpaRepository<FtpServerConfig, Long> {
    List<FtpServerConfig> findByEnabledTrue();
    Optional<FtpServerConfig> findByName(String name);
}
