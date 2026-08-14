package com.sonbadream_server.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminVerificationRequestDto {

    @NotBlank(message = "관리자 비밀번호는 필수입니다.")
    private String password;
}
