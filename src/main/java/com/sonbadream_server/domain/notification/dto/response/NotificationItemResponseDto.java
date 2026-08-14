package com.sonbadream_server.domain.notification.dto.response;

import com.sonbadream_server.domain.notification.entity.NotificationTypeCode;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationItemResponseDto {
    private Long notificationId;
    private NotificationTypeCode type;
    private String content;
    private boolean read;
    private LocalDateTime createdAt;
}
