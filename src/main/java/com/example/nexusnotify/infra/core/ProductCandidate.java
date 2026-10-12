package com.example.nexusnotify.infra.core;

import java.time.LocalDate;

public record ProductCandidate(
        Integer profileId,
        Integer itemId,
        String foodName,
        LocalDate expiryDate
) {}
