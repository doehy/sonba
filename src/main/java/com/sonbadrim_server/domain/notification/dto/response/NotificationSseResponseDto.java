package com.sonbadrim_server.domain.notification.dto.response;

import com.sonbadrim_server.domain.notification.entity.NotificationTypeCode;
import java.time.LocalDateTime;

public record NotificationSseResponseDto(
        Long notificationId,
        NotificationTypeCode type,
        String content,
        boolean isRead,
        LocalDateTime createdAt
) {
}
