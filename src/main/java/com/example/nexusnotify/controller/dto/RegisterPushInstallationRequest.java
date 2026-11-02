package com.example.nexusnotify.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterPushInstallationRequest(
        @Schema(description = "Firebase Installation ID, diferente do token FCM",
                pattern = "[A-Za-z0-9_-]{1,256}", example = "fid-installation-one")
        String installationId
) {
}
