package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationBroadcastWorkerTest {

    @InjectMocks
    private NotificationBroadcastWorker notificationBroadcastWorker;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationBroadcastProgressService notificationBroadcastProgressService;

    @Nested
    @DisplayName("공지 알림 발송 작업 처리")
    class Process {

        @Test
        @DisplayName("공지 알림 발송 작업은 사용자 ID를 일정 개수씩 나누어 처리하고 완료된다")
        void process_success() {
            // given
            PageRequest chunkRequest = PageRequest.of(0, 1_000);
            given(notificationBroadcastProgressService.start(1L))
                    .willReturn(new NotificationBroadcastJobSnapshot(2, "전체 공지입니다."));
            given(userRepository.findUserIdsByStatusAfterId(UserStatus.ACTIVE, 0L, chunkRequest))
                    .willReturn(List.of(1L, 2L));
            given(userRepository.findUserIdsByStatusAfterId(UserStatus.ACTIVE, 2L, chunkRequest))
                    .willReturn(List.of(5L));
            given(userRepository.findUserIdsByStatusAfterId(UserStatus.ACTIVE, 5L, chunkRequest))
                    .willReturn(List.of());

            // when
            notificationBroadcastWorker.process(1L);

            // then
            verify(notificationBroadcastProgressService).start(1L);
            verify(notificationBroadcastProgressService)
                    .saveChunk(1L, List.of(1L, 2L), 2, "전체 공지입니다.");
            verify(notificationBroadcastProgressService)
                    .saveChunk(1L, List.of(5L), 2, "전체 공지입니다.");
            verify(notificationBroadcastProgressService).complete(1L);
            verify(notificationBroadcastProgressService, never()).fail(eq(1L), eq("전체 공지입니다."));
        }

        @Test
        @DisplayName("공지 알림 발송 작업 중 예외가 발생하면 작업을 실패 처리한다")
        void process_failed() {
            // given
            PageRequest chunkRequest = PageRequest.of(0, 1_000);
            given(notificationBroadcastProgressService.start(1L))
                    .willReturn(new NotificationBroadcastJobSnapshot(2, "전체 공지입니다."));
            willThrow(new RuntimeException("사용자 조회 실패"))
                    .given(userRepository)
                    .findUserIdsByStatusAfterId(UserStatus.ACTIVE, 0L, chunkRequest);

            // when
            notificationBroadcastWorker.process(1L);

            // then
            verify(notificationBroadcastProgressService).start(1L);
            verify(notificationBroadcastProgressService).fail(1L, "사용자 조회 실패");
            verify(notificationBroadcastProgressService, never())
                    .saveChunk(eq(1L), eq(List.of(1L)), eq(2), eq("전체 공지입니다."));
            verify(notificationBroadcastProgressService, never()).complete(1L);
        }
    }
}
