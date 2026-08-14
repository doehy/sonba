package com.sonbadream_server.domain.inquiry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InquiryListItemResponseDto {
    private Long inquiryId;
    private String otherUserNickname;
    private String otherUserProfileImage;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
}
