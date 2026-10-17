package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.NearbyStoreRotation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NearbyStoreRotationRepository extends MongoRepository<NearbyStoreRotation, Integer> {}
