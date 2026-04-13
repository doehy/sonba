package com.sonbadrim_server.domain.inquiry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InquiryDetailMessageResponseDto {
    private Long messageId;
    private Long senderUserId;
    private String content;
    private LocalDateTime createdAt;
}
