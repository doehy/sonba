package com.sonbadrim_server.domain.notification.dto.response;

import com.sonbadrim_server.domain.notification.entity.NotificationBroadcastStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BroadcastNotificationStartResponseDto {

    private Long broadcastId;
    private NotificationBroadcastStatus status;
}
