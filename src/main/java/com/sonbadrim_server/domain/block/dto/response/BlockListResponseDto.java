package com.sonbadrim_server.domain.block.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class BlockListResponseDto {
    private List<BlockListItemResponseDto> items;
    private boolean hasNext;
}
