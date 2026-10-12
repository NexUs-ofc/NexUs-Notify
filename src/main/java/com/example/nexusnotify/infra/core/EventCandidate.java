package com.example.nexusnotify.infra.core;

import java.time.LocalDateTime;

public record EventCandidate(
        Integer profileId,
        String eventId,
        String title,
        LocalDateTime date
) {}
