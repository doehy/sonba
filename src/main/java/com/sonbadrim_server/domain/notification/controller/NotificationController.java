package com.sonbadrim_server.domain.notification.controller;

import com.sonbadrim_server.domain.notification.dto.response.NotificationListResponseDto;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.domain.notification.service.NotificationSseService;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;
    private final UserService userService;

    @Operation(summary = "알림 SSE 구독", description = "로그인 사용자의 실시간 알림 스트림을 구독합니다.")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        Long userId = userService.getCurrentUser().getUserId();
        return notificationSseService.subscribe(userId);
    }

    @Operation(summary = "내 알림 목록 조회", description = "내 알림을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<?> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        NotificationListResponseDto response = notificationService.getMyNotifications(pageable);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "알림 읽음 처리", description = "단일 알림을 읽음 처리합니다.")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "내 알림 전체를 읽음 처리합니다.")
    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
