package com.example.nexusnotify.controller.dto;

import com.example.nexusnotify.domain.PushInstallationStatus;

public record PushInstallationResponse(String id, PushInstallationStatus status) {
}
