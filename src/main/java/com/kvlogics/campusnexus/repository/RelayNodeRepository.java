package com.kvlogics.campusnexus.repository;

import com.kvlogics.campusnexus.model.RelayNode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelayNodeRepository extends MongoRepository<RelayNode, String> {
    Optional<RelayNode> findBySessionId(String sessionId);
    List<RelayNode> findByStatus(String status);
}
