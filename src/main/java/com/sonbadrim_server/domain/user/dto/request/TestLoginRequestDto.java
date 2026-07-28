package com.sonbadrim_server.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TestLoginRequestDto {

    @NotNull
    private Long userId;

    @NotBlank
    private String password;
}
