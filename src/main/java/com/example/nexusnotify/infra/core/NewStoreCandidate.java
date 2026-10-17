package com.example.nexusnotify.infra.core;

import java.time.LocalDate;
import java.util.List;

public record NewStoreCandidate(Integer storeId, String name, LocalDate createdDate, List<Integer> profileIds) {}
