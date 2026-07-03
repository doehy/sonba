package com.sonbadrim_server.domain.notification.controller;

import com.sonbadrim_server.domain.notification.dto.request.BroadcastNotificationRequestDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationResponseDto;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 알림 API", description = "관리자 알림 발송 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "공지 알림 발송", description = "활성 사용자 전체에게 공지 알림을 생성합니다.")
    @PostMapping("/broadcast")
    public ResponseEntity<CommonResponse<BroadcastNotificationResponseDto>> broadcastNotice(
            @Valid @RequestBody BroadcastNotificationRequestDto requestDto
    ) {
        BroadcastNotificationResponseDto response = notificationService.broadcastNotice(requestDto);
        return ResponseEntity.ok(CommonResponse.success(response));
    }
}
