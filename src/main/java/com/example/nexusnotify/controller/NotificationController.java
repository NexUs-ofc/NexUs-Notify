package com.example.nexusnotify.controller;

import com.example.nexusnotify.controller.dto.NotificationResponse;
import com.example.nexusnotify.controller.dto.SliceResponse;
import com.example.nexusnotify.service.NotificationService;
import com.example.nexusnotify.util.CurrentProfileResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notificações")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentProfileResolver currentProfile;

    public NotificationController(NotificationService service, CurrentProfileResolver currentProfile) {
        this.service = service;
        this.currentProfile = currentProfile;
    }

    @Operation(summary = "Consultar meu histórico de notificações",
            description = "Lista notificações do perfil do JWT, ordenadas por criação; hasNext indica a "
                    + "existência de outra página.")
    @GetMapping
    public SliceResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(currentProfile.profileId(), page, size);
    }
}
