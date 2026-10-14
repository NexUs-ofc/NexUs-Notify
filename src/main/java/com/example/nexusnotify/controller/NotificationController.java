package com.example.nexusnotify.controller;

import com.example.nexusnotify.controller.dto.NotificationResponse;
import com.example.nexusnotify.controller.dto.SliceResponse;
import com.example.nexusnotify.service.NotificationService;
import com.example.nexusnotify.util.CurrentProfileResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentProfileResolver currentProfile;

    public NotificationController(NotificationService service, CurrentProfileResolver currentProfile) {
        this.service = service;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public SliceResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(currentProfile.profileId(), page, size);
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable String id) {
        service.markAsRead(currentProfile.profileId(), id);
    }
}
