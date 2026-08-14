package com.sonbadream_server.domain.inquiry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SendInquiryMessageRequestDto {

    @NotBlank(message = "문의 메시지는 필수입니다.")
    @Size(max = 500, message = "메시지는 500자 이하로 입력해주세요.")
    private String message;
}
