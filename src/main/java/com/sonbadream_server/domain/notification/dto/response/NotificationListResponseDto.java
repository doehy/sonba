package com.sonbadream_server.domain.notification.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationListResponseDto {
    private List<NotificationItemResponseDto> items;
    private boolean hasNext;
    private long unreadCount;
}
