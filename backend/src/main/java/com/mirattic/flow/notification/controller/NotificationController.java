package com.mirattic.flow.notification.controller;

import com.mirattic.flow.global.security.AuthUser;
import com.mirattic.flow.notification.dto.NotificationResponse;
import com.mirattic.flow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> findAll(@AuthenticationPrincipal AuthUser authUser) {
        return notificationService.findRecent(authUser.id());
    }

    /** 배지에 숫자만 띄우면 되므로 목록과 따로 둔다. 30초마다 부르는 쪽이 가벼워진다. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthUser authUser) {
        return Map.of("count", notificationService.unreadCount(authUser.id()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        notificationService.markRead(id, authUser.id());
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser authUser) {
        notificationService.markAllRead(authUser.id());
    }
}
