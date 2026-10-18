package com.example.nexusnotify.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "new_store_discovery_state")
public record NewStoreDiscoveryState(@Id String id, @Field("scanned_through") String scannedThrough) {}
