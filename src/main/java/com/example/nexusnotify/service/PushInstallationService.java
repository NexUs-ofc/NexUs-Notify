package com.example.nexusnotify.service;

import com.example.nexusnotify.controller.dto.PushInstallationResponse;
import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.infra.repository.PushInstallationRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class PushInstallationService {

    private static final Pattern INSTALLATION_ID = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    private final PushInstallationRepository repository;
    private final Clock clock;

    public PushInstallationService(PushInstallationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PushInstallationResponse register(Integer profileId, String installationId) {
        if (installationId == null || !INSTALLATION_ID.matcher(installationId).matches()) {
            throw new IllegalArgumentException("Firebase Installation ID invalido");
        }
        PushInstallation installation = repository.register(profileId, installationId, Instant.now(clock));
        return new PushInstallationResponse(installation.id().toHexString(), installation.status());
    }

    public void revoke(Integer profileId, String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new IllegalArgumentException("Id do cadastro de instalacao invalido");
        }
        repository.revoke(new ObjectId(id), profileId, Instant.now(clock));
    }
}
