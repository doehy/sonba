package com.sonbadream_server.domain.notification.service;

record NotificationBroadcastJobSnapshot(
        Integer notificationTypeId,
        String content,
        long lastProcessedUserId
) {
}
