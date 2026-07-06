package com.sonbadrim_server.domain.notification.controller;

import com.sonbadrim_server.domain.notification.dto.request.BroadcastNotificationRequestDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationStartResponseDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationStatusResponseDto;
import com.sonbadrim_server.domain.notification.entity.NotificationBroadcastStatus;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminNotificationController.class)
class AdminNotificationControllerTest {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("공지 알림 발송")
    class BroadcastNotice {

        @Test
        @DisplayName("관리자는 공지 알림 발송을 요청할 수 있다")
        void broadcastNotice_success() throws Exception {
            // given
            BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                    .content("서비스 점검 안내입니다.")
                    .build();

            given(notificationService.startBroadcastNotice(any(BroadcastNotificationRequestDto.class)))
                    .willReturn(new BroadcastNotificationStartResponseDto(1L, NotificationBroadcastStatus.PENDING));

            // when & then
            mockMvc.perform(post("/admin/notifications/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.broadcastId").value(1L))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));

            ArgumentCaptor<BroadcastNotificationRequestDto> captor =
                    ArgumentCaptor.forClass(BroadcastNotificationRequestDto.class);
            verify(notificationService).startBroadcastNotice(captor.capture());

            assertThat(captor.getValue().getContent()).isEqualTo("서비스 점검 안내입니다.");
        }

        @Test
        @DisplayName("공지 알림 내용이 없으면 발송을 요청할 수 없다")
        void broadcastNotice_contentIsBlank() throws Exception {
            // given
            BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                    .content(" ")
                    .build();

            // when & then
            mockMvc.perform(post("/admin/notifications/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("GLOBAL_400_1"))
                    .andExpect(jsonPath("$.data.validation.content").exists());

            verify(notificationService, never())
                    .startBroadcastNotice(any(BroadcastNotificationRequestDto.class));
        }

        @Test
        @DisplayName("관리자가 아니면 공지 알림 발송을 요청할 수 없다")
        void broadcastNotice_forbidden() throws Exception {
            // given
            BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                    .content("서비스 점검 안내입니다.")
                    .build();

            willThrow(new CustomException(ErrorCode.FORBIDDEN))
                    .given(notificationService)
                    .startBroadcastNotice(any(BroadcastNotificationRequestDto.class));

            // when & then
            mockMvc.perform(post("/admin/notifications/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("공지 알림 발송 작업 조회")
    class GetBroadcastStatus {

        @Test
        @DisplayName("관리자는 공지 알림 발송 작업의 진행 상태를 조회할 수 있다")
        void getBroadcastStatus_success() throws Exception {
            // given
            given(notificationService.getBroadcastStatus(1L))
                    .willReturn(new BroadcastNotificationStatusResponseDto(
                            1L,
                            NotificationBroadcastStatus.PROCESSING,
                            100_000L,
                            30_000L,
                            0L,
                            null,
                            LocalDateTime.of(2026, 7, 5, 12, 0),
                            LocalDateTime.of(2026, 7, 5, 12, 1)
                    ));

            // when & then
            mockMvc.perform(get("/admin/notifications/broadcasts/{broadcastId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.broadcastId").value(1L))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.totalTargetCount").value(100_000L))
                    .andExpect(jsonPath("$.data.processedCount").value(30_000L))
                    .andExpect(jsonPath("$.data.failedCount").value(0L));

            verify(notificationService).getBroadcastStatus(1L);
        }

        @Test
        @DisplayName("실패한 공지 알림 발송 작업의 실패 사유를 조회할 수 있다")
        void getBroadcastStatus_failed() throws Exception {
            // given
            given(notificationService.getBroadcastStatus(1L))
                    .willReturn(new BroadcastNotificationStatusResponseDto(
                            1L,
                            NotificationBroadcastStatus.FAILED,
                            100_000L,
                            30_000L,
                            70_000L,
                            "사용자 조회 실패",
                            LocalDateTime.of(2026, 7, 5, 12, 0),
                            LocalDateTime.of(2026, 7, 5, 12, 1)
                    ));

            // when & then
            mockMvc.perform(get("/admin/notifications/broadcasts/{broadcastId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.broadcastId").value(1L))
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.totalTargetCount").value(100_000L))
                    .andExpect(jsonPath("$.data.processedCount").value(30_000L))
                    .andExpect(jsonPath("$.data.failedCount").value(70_000L))
                    .andExpect(jsonPath("$.data.failureReason").value("사용자 조회 실패"));

            verify(notificationService).getBroadcastStatus(1L);
        }

        @Test
        @DisplayName("존재하지 않는 공지 알림 발송 작업은 조회할 수 없다")
        void getBroadcastStatus_notFound() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.NOTIFICATION_BROADCAST_NOT_FOUND))
                    .given(notificationService)
                    .getBroadcastStatus(999L);

            // when & then
            mockMvc.perform(get("/admin/notifications/broadcasts/{broadcastId}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("NOTIFICATION_BROADCAST_NOT_FOUND"));

            verify(notificationService).getBroadcastStatus(999L);
        }
    }
}
