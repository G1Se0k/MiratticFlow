package com.mirattic.flow.notification.dto;

import com.mirattic.flow.notification.entity.Notification;
import com.mirattic.flow.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String content,
        String link,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getContent(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
