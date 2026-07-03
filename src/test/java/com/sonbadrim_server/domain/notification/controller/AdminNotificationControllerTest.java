package com.sonbadrim_server.domain.notification.controller;

import com.sonbadrim_server.domain.notification.dto.request.BroadcastNotificationRequestDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationResponseDto;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
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

            given(notificationService.broadcastNotice(any(BroadcastNotificationRequestDto.class)))
                    .willReturn(new BroadcastNotificationResponseDto(10L));

            // when & then
            mockMvc.perform(post("/admin/notifications/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.notificationCount").value(10L));

            ArgumentCaptor<BroadcastNotificationRequestDto> captor =
                    ArgumentCaptor.forClass(BroadcastNotificationRequestDto.class);
            verify(notificationService).broadcastNotice(captor.capture());

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
                    .broadcastNotice(any(BroadcastNotificationRequestDto.class));
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
                    .broadcastNotice(any(BroadcastNotificationRequestDto.class));

            // when & then
            mockMvc.perform(post("/admin/notifications/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }
    }
}
