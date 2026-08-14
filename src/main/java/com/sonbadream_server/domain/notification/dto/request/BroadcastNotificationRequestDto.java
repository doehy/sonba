package com.sonbadream_server.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BroadcastNotificationRequestDto {

    @NotBlank(message = "공지 알림 내용은 필수입니다.")
    @Size(max = 255, message = "공지 알림 내용은 255자 이하로 입력해주세요.")
    private String content;
}
