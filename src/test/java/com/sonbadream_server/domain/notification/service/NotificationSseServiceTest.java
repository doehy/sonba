package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.entity.Notification;
import com.sonbadream_server.domain.notification.entity.NotificationType;
import com.sonbadream_server.domain.notification.entity.NotificationTypeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class NotificationSseServiceTest {

    private final SseEmitterRegistry sseEmitterRegistry = new SseEmitterRegistry();
    private final NotificationSseService notificationSseService = new NotificationSseService(sseEmitterRegistry);

    @Nested
    @DisplayName("알림 SSE 구독")
    class Subscribe {

        @Test
        @DisplayName("사용자가 SSE를 구독하면 연결 정보가 저장된다")
        void subscribe_success() {
            // when
            SseEmitter emitter = notificationSseService.subscribe(1L);

            // then
            assertThat(emitter).isNotNull();
            assertThat(sseEmitterRegistry.hasEmitter(1L)).isTrue();
            assertThat(sseEmitterRegistry.countByUserId(1L)).isEqualTo(1);
            assertThat(sseEmitterRegistry.findByUserId(1L))
                    .extracting(SseEmitterRegistry.SseEmitterConnection::emitter)
                    .containsExactly(emitter);
        }

        @Test
        @DisplayName("같은 사용자가 여러 번 구독하면 연결 정보가 각각 저장된다")
        void subscribe_sameUserMultipleConnections() {
            // when
            SseEmitter firstEmitter = notificationSseService.subscribe(1L);
            SseEmitter secondEmitter = notificationSseService.subscribe(1L);

            // then
            assertThat(sseEmitterRegistry.countByUserId(1L)).isEqualTo(2);
            assertThat(sseEmitterRegistry.findByUserId(1L))
                    .extracting(SseEmitterRegistry.SseEmitterConnection::emitter)
                    .containsExactlyInAnyOrder(firstEmitter, secondEmitter);
        }

        @Test
        @DisplayName("서로 다른 사용자의 구독 정보는 사용자별로 분리되어 저장된다")
        void subscribe_differentUsers() {
            // when
            SseEmitter firstUserEmitter = notificationSseService.subscribe(1L);
            SseEmitter secondUserEmitter = notificationSseService.subscribe(2L);

            // then
            assertThat(sseEmitterRegistry.hasEmitter(1L)).isTrue();
            assertThat(sseEmitterRegistry.hasEmitter(2L)).isTrue();
            assertThat(sseEmitterRegistry.findByUserId(1L))
                    .extracting(SseEmitterRegistry.SseEmitterConnection::emitter)
                    .containsExactly(firstUserEmitter);
            assertThat(sseEmitterRegistry.findByUserId(2L))
                    .extracting(SseEmitterRegistry.SseEmitterConnection::emitter)
                    .containsExactly(secondUserEmitter);
        }

    }

    @Nested
    @DisplayName("알림 이벤트 전송")
    class SendNotificationEvent {

        @Test
        @DisplayName("연결된 사용자가 없으면 알림 이벤트를 전송하지 않는다")
        void sendNotificationEvent_noEmitter() {
            // given
            Notification notification = createNotification();

            // when
            boolean result = notificationSseService.sendNotificationEvent(1L, notification);

            // then
            assertThat(result).isFalse();
            assertThat(sseEmitterRegistry.hasEmitter(1L)).isFalse();
        }

        @Test
        @DisplayName("연결된 사용자에게 알림 이벤트를 전송할 수 있다")
        void sendNotificationEvent_success() {
            // given
            CapturingSseEmitter emitter = new CapturingSseEmitter();
            sseEmitterRegistry.add(1L, emitter);
            Notification notification = createNotification();

            // when
            boolean result = notificationSseService.sendNotificationEvent(1L, notification);

            // then
            assertThat(result).isTrue();
            assertThat(emitter.getSendCount()).isEqualTo(1);
            assertThat(sseEmitterRegistry.hasEmitter(1L)).isTrue();
        }

        @Test
        @DisplayName("알림 이벤트 전송에 실패한 연결 정보는 제거된다")
        void sendNotificationEvent_removesFailedEmitter() {
            // given
            CapturingSseEmitter failedEmitter = new CapturingSseEmitter(true);
            sseEmitterRegistry.add(1L, failedEmitter);
            Notification notification = createNotification();

            // when
            boolean result = notificationSseService.sendNotificationEvent(1L, notification);

            // then
            assertThat(result).isFalse();
            assertThat(sseEmitterRegistry.hasEmitter(1L)).isFalse();
        }

        @Test
        @DisplayName("일부 연결 전송에 실패해도 성공한 연결이 있으면 전송 성공으로 처리한다")
        void sendNotificationEvent_partiallyFailed() {
            // given
            CapturingSseEmitter successEmitter = new CapturingSseEmitter();
            CapturingSseEmitter failedEmitter = new CapturingSseEmitter(true);
            sseEmitterRegistry.add(1L, successEmitter);
            sseEmitterRegistry.add(1L, failedEmitter);
            Notification notification = createNotification();

            // when
            boolean result = notificationSseService.sendNotificationEvent(1L, notification);

            // then
            assertThat(result).isTrue();
            assertThat(successEmitter.getSendCount()).isEqualTo(1);
            assertThat(sseEmitterRegistry.countByUserId(1L)).isEqualTo(1);
            assertThat(sseEmitterRegistry.findByUserId(1L))
                    .extracting(SseEmitterRegistry.SseEmitterConnection::emitter)
                    .containsExactly(successEmitter);
        }
    }

    @Nested
    @DisplayName("SSE heartbeat 전송")
    class SendHeartbeat {

        @Test
        @DisplayName("연결된 사용자에게 heartbeat를 전송할 수 있다")
        void sendHeartbeat_success() {
            // given
            CapturingSseEmitter firstEmitter = new CapturingSseEmitter();
            CapturingSseEmitter secondEmitter = new CapturingSseEmitter();
            sseEmitterRegistry.add(1L, firstEmitter);
            sseEmitterRegistry.add(2L, secondEmitter);

            // when
            notificationSseService.sendHeartbeat();

            // then
            assertThat(firstEmitter.getSendCount()).isEqualTo(1);
            assertThat(secondEmitter.getSendCount()).isEqualTo(1);
            assertThat(sseEmitterRegistry.hasEmitter(1L)).isTrue();
            assertThat(sseEmitterRegistry.hasEmitter(2L)).isTrue();
        }

        @Test
        @DisplayName("heartbeat 전송에 실패한 연결 정보는 제거된다")
        void sendHeartbeat_removesFailedEmitter() {
            // given
            CapturingSseEmitter successEmitter = new CapturingSseEmitter();
            CapturingSseEmitter failedEmitter = new CapturingSseEmitter(true);
            sseEmitterRegistry.add(1L, successEmitter);
            sseEmitterRegistry.add(1L, failedEmitter);

            // when
            notificationSseService.sendHeartbeat();

            // then
            assertThat(successEmitter.getSendCount()).isEqualTo(1);
            assertThat(sseEmitterRegistry.countByUserId(1L)).isEqualTo(1);
            assertThat(sseEmitterRegistry.findByUserId(1L))
                    .extracting(SseEmitterRegistry.SseEmitterConnection::emitter)
                    .containsExactly(successEmitter);
        }
    }

    private Notification createNotification() {
        NotificationType notificationType = mock(NotificationType.class);
        given(notificationType.getType()).willReturn(NotificationTypeCode.INQUIRY);

        Notification notification = mock(Notification.class);
        given(notification.getNotificationId()).willReturn(1L);
        given(notification.getNotificationType()).willReturn(notificationType);
        given(notification.getContent()).willReturn("새 문의가 도착했습니다.");
        given(notification.isRead()).willReturn(false);
        given(notification.getCreatedAt()).willReturn(LocalDateTime.of(2026, 6, 25, 12, 0));
        return notification;
    }

    private static class CapturingSseEmitter extends SseEmitter {

        private final boolean failOnSend;
        private int sendCount;

        private CapturingSseEmitter() {
            this(false);
        }

        private CapturingSseEmitter(boolean failOnSend) {
            super(60_000L);
            this.failOnSend = failOnSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("SSE 전송 실패");
            }
            sendCount++;
        }

        private int getSendCount() {
            return sendCount;
        }
    }
}
