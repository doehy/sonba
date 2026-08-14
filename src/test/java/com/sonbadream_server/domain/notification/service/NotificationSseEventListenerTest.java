package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.entity.Notification;
import com.sonbadream_server.domain.notification.repository.NotificationRepository;
import com.sonbadream_server.domain.region.entity.Region;
import com.sonbadream_server.domain.region.repository.RegionRepository;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.domain.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/sql/notification-test-data.sql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationSseEventListenerTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private Region region;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
    }

    @Nested
    @DisplayName("커밋 이후 실시간 알림 전송")
    class SendAfterCommit {

        @Test
        @DisplayName("알림 저장 트랜잭션이 커밋된 뒤 실시간 알림을 전송한다")
        void sendAfterCommit_success() {
            // given
            User targetUser = saveUser(900000000002L, "알림대상");
            given(notificationSseService.sendNotificationEvent(eq(targetUser.getUserId()), any(Notification.class)))
                    .willReturn(true);

            // when
            notificationService.createInquiryNotification(targetUser.getUserId(), "김문의");

            // then
            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> {
                        entityManager.clear();
                        Notification notification = notificationRepository.findAll().getFirst();
                        verify(notificationSseService)
                                .sendNotificationEvent(eq(targetUser.getUserId()), any(Notification.class));
                        assertThat(notification.isSseSent()).isTrue();
                    });
        }

        @Test
        @DisplayName("실시간 알림 전송에 실패해도 알림 저장 상태는 유지된다")
        void sendAfterCommit_sseFailed() {
            // given
            User targetUser = saveUser(900000000002L, "알림대상");
            given(notificationSseService.sendNotificationEvent(eq(targetUser.getUserId()), any(Notification.class)))
                    .willReturn(false);

            // when
            notificationService.createInquiryNotification(targetUser.getUserId(), "김문의");

            // then
            await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> {
                        entityManager.clear();
                        Notification notification = notificationRepository.findAll().getFirst();
                        verify(notificationSseService)
                                .sendNotificationEvent(eq(targetUser.getUserId()), any(Notification.class));
                        assertThat(notification.getContent()).isEqualTo("김문의님이 새 문의를 보냈습니다.");
                        assertThat(notification.isSseSent()).isFalse();
                    });
        }
    }

    private User saveUser(Long kakaoId, String nickname) {
        return userRepository.save(User.builder()
                .region(region)
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }
}
