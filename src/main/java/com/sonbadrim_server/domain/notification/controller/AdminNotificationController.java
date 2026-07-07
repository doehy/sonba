package com.sonbadrim_server.domain.notification.controller;

import com.sonbadrim_server.domain.notification.dto.request.BroadcastNotificationRequestDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationStartResponseDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationStatusResponseDto;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "공지 알림 발송 요청", description = "활성 사용자 전체에게 공지 알림을 생성하는 비동기 작업을 시작합니다.")
    @PostMapping("/broadcast")
    public ResponseEntity<CommonResponse<BroadcastNotificationStartResponseDto>> startBroadcastNotice(
            @Valid @RequestBody BroadcastNotificationRequestDto requestDto
    ) {
        BroadcastNotificationStartResponseDto response = notificationService.startBroadcastNotice(requestDto);
        return ResponseEntity.accepted().body(CommonResponse.success(response));
    }

    @Operation(summary = "공지 알림 발송 작업 조회", description = "공지 알림 발송 작업의 진행 상태를 조회합니다.")
    @GetMapping("/broadcasts/{broadcastId}")
    public ResponseEntity<CommonResponse<BroadcastNotificationStatusResponseDto>> getBroadcastStatus(
            @PathVariable Long broadcastId
    ) {
        BroadcastNotificationStatusResponseDto response = notificationService.getBroadcastStatus(broadcastId);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "공지 알림 발송 작업 재시도", description = "실패한 공지 알림 발송 작업을 마지막 처리 사용자 이후부터 재시도합니다.")
    @PostMapping("/broadcasts/{broadcastId}/retry")
    public ResponseEntity<CommonResponse<BroadcastNotificationStartResponseDto>> retryBroadcastNotice(
            @PathVariable Long broadcastId
    ) {
        BroadcastNotificationStartResponseDto response = notificationService.retryBroadcastNotice(broadcastId);
        return ResponseEntity.accepted().body(CommonResponse.success(response));
    }
}
