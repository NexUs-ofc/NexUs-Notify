package com.example.nexusnotify.controller;

import com.example.nexusnotify.controller.dto.PushInstallationResponse;
import com.example.nexusnotify.controller.dto.RegisterPushInstallationRequest;
import com.example.nexusnotify.service.PushInstallationService;
import com.example.nexusnotify.util.CurrentProfileResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push/installations")
public class PushInstallationController {

    private final PushInstallationService service;
    private final CurrentProfileResolver currentProfile;

    public PushInstallationController(PushInstallationService service, CurrentProfileResolver currentProfile) {
        this.service = service;
        this.currentProfile = currentProfile;
    }

    @PutMapping
    public PushInstallationResponse register(@RequestBody RegisterPushInstallationRequest request) {
        return service.register(currentProfile.profileId(), request.installationId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String id) {
        service.revoke(currentProfile.profileId(), id);
    }
}
