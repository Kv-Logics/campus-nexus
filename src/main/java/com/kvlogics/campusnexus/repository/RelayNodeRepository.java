package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.RelayNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelayNodeRepository extends JpaRepository<RelayNode, Long> {
    Optional<RelayNode> findBySessionId(String sessionId);
    List<RelayNode> findByStatus(String status);
}
