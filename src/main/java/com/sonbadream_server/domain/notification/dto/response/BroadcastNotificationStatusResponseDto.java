package com.sonbadream_server.domain.notification.dto.response;

import com.sonbadream_server.domain.notification.entity.NotificationBroadcastStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BroadcastNotificationStatusResponseDto {

    private Long broadcastId;
    private NotificationBroadcastStatus status;
    private long totalTargetCount;
    private long processedCount;
    private long failedCount;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
