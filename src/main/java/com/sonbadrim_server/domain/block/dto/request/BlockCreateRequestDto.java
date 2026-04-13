package com.sonbadrim_server.domain.block.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BlockCreateRequestDto {

    @NotNull(message = "차단 대상 userId는 필수입니다.")
    private Long blockedUserId;
}
