package com.sonbadrim_server.domain.notification.controller;

import com.sonbadrim_server.domain.notification.dto.response.NotificationItemResponseDto;
import com.sonbadrim_server.domain.notification.dto.response.NotificationListResponseDto;
import com.sonbadrim_server.domain.notification.entity.NotificationTypeCode;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.domain.notification.service.NotificationSseService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("알림 목록 조회")
    class GetMyNotifications {

        @Test
        @DisplayName("알림 목록을 페이지 단위로 조회할 수 있다")
        void getMyNotifications_success() throws Exception {
            // given
            NotificationItemResponseDto item = new NotificationItemResponseDto(
                    1L,
                    NotificationTypeCode.INQUIRY,
                    "새 문의가 도착했습니다.",
                    false,
                    LocalDateTime.of(2026, 6, 24, 12, 0)
            );
            given(notificationService.getMyNotifications(any(Pageable.class)))
                    .willReturn(new NotificationListResponseDto(List.of(item), false, 1));

            // when & then
            mockMvc.perform(get("/notifications")
                            .param("page", "2")
                            .param("size", "15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items[0].notificationId").value(1L))
                    .andExpect(jsonPath("$.data.items[0].type").value("INQUIRY"))
                    .andExpect(jsonPath("$.data.items[0].content").value("새 문의가 도착했습니다."))
                    .andExpect(jsonPath("$.data.items[0].read").value(false))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.unreadCount").value(1));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(notificationService).getMyNotifications(captor.capture());

            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(15);
        }

        @Test
        @DisplayName("페이지 크기가 100을 초과하면 알림 목록을 조회할 수 없다")
        void getMyNotifications_sizeTooLarge() throws Exception {
            // when & then
            expectValidationFailure("size", "101");
        }

        @Test
        @DisplayName("페이지 크기가 1보다 작으면 알림 목록을 조회할 수 없다")
        void getMyNotifications_sizeTooSmall() throws Exception {
            // when & then
            expectValidationFailure("size", "0");
        }

        @Test
        @DisplayName("페이지 번호가 0보다 작으면 알림 목록을 조회할 수 없다")
        void getMyNotifications_pageTooSmall() throws Exception {
            // when & then
            expectValidationFailure("page", "-1");
        }
    }

    private void expectValidationFailure(String parameterName, String value) throws Exception {
        mockMvc.perform(get("/notifications")
                        .param(parameterName, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("GLOBAL_400_1"))
                .andExpect(jsonPath("$.data.validation." + parameterName).exists());

        verify(notificationService, never())
                .getMyNotifications(any(Pageable.class));
    }
}
