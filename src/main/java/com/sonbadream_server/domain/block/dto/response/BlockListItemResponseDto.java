package com.sonbadream_server.domain.block.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BlockListItemResponseDto {
    private Long blockedUserId;
    private String blockedNickname;
    private String blockedProfileImage;
    private LocalDateTime blockedAt;
}
