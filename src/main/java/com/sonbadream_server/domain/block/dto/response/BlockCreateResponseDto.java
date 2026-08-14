package com.sonbadream_server.domain.block.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BlockCreateResponseDto {
    private Long blockedUserId;
    private boolean blockCreated;
}
