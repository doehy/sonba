package com.sonbadream_server.domain.notification.event;

public record NotificationSseSendEvent(
        Long userId,
        Long notificationId
) {
}
