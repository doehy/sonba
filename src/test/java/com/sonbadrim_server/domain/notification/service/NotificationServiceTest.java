package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.notification.dto.request.BroadcastNotificationRequestDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationStartResponseDto;
import com.sonbadrim_server.domain.notification.dto.response.BroadcastNotificationStatusResponseDto;
import com.sonbadrim_server.domain.notification.dto.response.NotificationListResponseDto;
import com.sonbadrim_server.domain.notification.entity.Notification;
import com.sonbadrim_server.domain.notification.entity.NotificationBroadcast;
import com.sonbadrim_server.domain.notification.entity.NotificationBroadcastStatus;
import com.sonbadrim_server.domain.notification.entity.NotificationType;
import com.sonbadrim_server.domain.notification.entity.NotificationTypeCode;
import com.sonbadrim_server.domain.notification.repository.NotificationBroadcastRepository;
import com.sonbadrim_server.domain.notification.repository.NotificationRepository;
import com.sonbadrim_server.domain.notification.repository.NotificationTypeRepository;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/notification-test-data.sql")
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationBroadcastRepository notificationBroadcastRepository;

    @Autowired
    private NotificationTypeRepository notificationTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    private Region region;
    private NotificationType inquiryType;
    private User currentUser;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        inquiryType = notificationTypeRepository.findByType(NotificationTypeCode.INQUIRY)
                .orElseThrow();
        currentUser = saveUser(900000000001L, "현재사용자");

        given(userService.getCurrentUser()).willReturn(currentUser);
    }

    @Nested
    @DisplayName("내 알림 목록 조회")
    class GetMyNotifications {

        @Test
        @DisplayName("내 알림 목록을 최신순으로 조회하고 읽지 않은 알림 수를 함께 확인할 수 있다")
        void getMyNotifications_success() {
            // given
            Notification oldUnread = saveNotification(currentUser, "오래된 문의 알림");
            Notification middleRead = saveNotification(currentUser, "읽은 문의 알림");
            middleRead.markRead();
            Notification latestUnread = saveNotification(currentUser, "최신 문의 알림");

            User otherUser = saveUser(900000000002L, "다른사용자");
            saveNotification(otherUser, "다른 사용자 알림");

            entityManager.flush();
            updateCreatedAt(oldUnread, LocalDateTime.of(2026, 6, 24, 10, 0));
            updateCreatedAt(middleRead, LocalDateTime.of(2026, 6, 24, 11, 0));
            updateCreatedAt(latestUnread, LocalDateTime.of(2026, 6, 24, 12, 0));
            entityManager.clear();

            // when
            NotificationListResponseDto result =
                    notificationService.getMyNotifications(PageRequest.of(0, 10));

            // then
            assertThat(result.getItems()).hasSize(3);
            assertThat(result.isHasNext()).isFalse();
            assertThat(result.getUnreadCount()).isEqualTo(2L);

            assertThat(result.getItems())
                    .extracting(item -> item.getContent())
                    .containsExactly(
                            "최신 문의 알림",
                            "읽은 문의 알림",
                            "오래된 문의 알림"
                    );
            assertThat(result.getItems())
                    .extracting(item -> item.getType())
                    .containsOnly(NotificationTypeCode.INQUIRY);
            assertThat(result.getItems())
                    .extracting(item -> item.isRead())
                    .containsExactly(false, true, false);
            assertThat(result.getItems())
                    .allSatisfy(item -> {
                        assertThat(item.getNotificationId()).isNotNull();
                        assertThat(item.getCreatedAt()).isNotNull();
                    });
        }

        @Test
        @DisplayName("다음 페이지가 있으면 알림 목록에 다음 페이지 존재 여부를 함께 반환한다")
        void getMyNotifications_hasNext() {
            // given
            saveNotification(currentUser, "첫 번째 알림");
            saveNotification(currentUser, "두 번째 알림");

            entityManager.flush();
            entityManager.clear();

            // when
            NotificationListResponseDto result =
                    notificationService.getMyNotifications(PageRequest.of(0, 1));

            // then
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.isHasNext()).isTrue();
            assertThat(result.getUnreadCount()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("알림 생성")
    class CreateNotification {

        @Test
        @DisplayName("문의 알림을 생성할 수 있다")
        void createInquiryNotification_success() {
            // given
            User targetUser = saveUser(900000000002L, "알림대상");

            // when
            notificationService.createInquiryNotification(targetUser.getUserId(), "김문의");

            // then
            Notification notification = notificationRepository.findAll().getFirst();
            assertThat(notification.getUser().getUserId()).isEqualTo(targetUser.getUserId());
            assertThat(notification.getNotificationType().getType()).isEqualTo(NotificationTypeCode.INQUIRY);
            assertThat(notification.getContent()).isEqualTo("김문의님이 새 문의를 보냈습니다.");
            assertThat(notification.isRead()).isFalse();
            assertThat(notification.isSseSent()).isFalse();
        }

        @Test
        @DisplayName("보낸 사람 이름이 없으면 기본 이름으로 알림을 생성한다")
        void createInquiryNotification_senderNameIsNull() {
            // given
            User targetUser = saveUser(900000000002L, "알림대상");

            // when
            notificationService.createInquiryNotification(targetUser.getUserId(), null);

            // then
            Notification notification = notificationRepository.findAll().getFirst();
            assertThat(notification.getContent()).isEqualTo("알 수 없는 사용자님이 새 문의를 보냈습니다.");
        }

        @Test
        @DisplayName("존재하지 않는 사용자에게 알림을 생성할 수 없다")
        void createInquiryNotification_userNotFound() {
            // when & then
            assertThatThrownBy(() -> notificationService.createInquiryNotification(999L, "김문의"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            assertThat(notificationRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("공지 알림 발송")
    class BroadcastNotice {

        @Test
        @DisplayName("관리자가 아니면 공지 알림 발송 작업을 시작할 수 없다")
        void startBroadcastNotice_forbidden() {
            // given
            BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                    .content("서비스 점검 안내입니다.")
                    .build();

            // when & then
            assertThatThrownBy(() -> notificationService.startBroadcastNotice(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);

            assertThat(notificationRepository.findAll()).isEmpty();
            assertThat(notificationBroadcastRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("관리자는 공지 알림 발송 작업을 시작할 수 있다")
        void startBroadcastNotice_success() {
            // given
            User adminUser = saveUser(900000000006L, "비동기관리자", UserRole.ADMIN, UserStatus.ACTIVE);
            saveUser(900000000007L, "비동기대상");

            given(userService.getCurrentUser()).willReturn(adminUser);

            BroadcastNotificationRequestDto request = BroadcastNotificationRequestDto.builder()
                    .content("  전체 공지입니다.  ")
                    .build();

            // when
            BroadcastNotificationStartResponseDto response = notificationService.startBroadcastNotice(request);

            // then
            assertThat(response.getBroadcastId()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(NotificationBroadcastStatus.PENDING);

            NotificationBroadcast broadcast = notificationBroadcastRepository.findById(response.getBroadcastId())
                    .orElseThrow();
            assertThat(broadcast.getNotificationType().getType()).isEqualTo(NotificationTypeCode.NOTICE);
            assertThat(broadcast.getContent()).isEqualTo("전체 공지입니다.");
            assertThat(broadcast.getStatus()).isEqualTo(NotificationBroadcastStatus.PENDING);
            assertThat(broadcast.getTotalTargetCount()).isEqualTo(3L);
            assertThat(broadcast.getProcessedCount()).isZero();
            assertThat(broadcast.getFailedCount()).isZero();
        }

        @Test
        @DisplayName("관리자는 공지 알림 발송 작업의 진행 상태를 조회할 수 있다")
        void getBroadcastStatus_success() {
            // given
            User adminUser = saveUser(900000000008L, "상태조회관리자", UserRole.ADMIN, UserStatus.ACTIVE);
            NotificationType noticeType = notificationTypeRepository.findByType(NotificationTypeCode.NOTICE)
                    .orElseThrow();
            NotificationBroadcast broadcast = notificationBroadcastRepository.save(
                    NotificationBroadcast.create(noticeType, "전체 공지입니다.", 10L)
            );

            given(userService.getCurrentUser()).willReturn(adminUser);

            // when
            BroadcastNotificationStatusResponseDto response =
                    notificationService.getBroadcastStatus(broadcast.getNotificationBroadcastId());

            // then
            assertThat(response.getBroadcastId()).isEqualTo(broadcast.getNotificationBroadcastId());
            assertThat(response.getStatus()).isEqualTo(NotificationBroadcastStatus.PENDING);
            assertThat(response.getTotalTargetCount()).isEqualTo(10L);
            assertThat(response.getProcessedCount()).isZero();
            assertThat(response.getFailedCount()).isZero();
        }

        @Test
        @DisplayName("관리자가 아니면 공지 알림 발송 작업을 조회할 수 없다")
        void getBroadcastStatus_forbidden() {
            // when & then
            assertThatThrownBy(() -> notificationService.getBroadcastStatus(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("존재하지 않는 공지 알림 발송 작업은 조회할 수 없다")
        void getBroadcastStatus_notFound() {
            // given
            User adminUser = saveUser(900000000009L, "없는작업조회관리자", UserRole.ADMIN, UserStatus.ACTIVE);
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when & then
            assertThatThrownBy(() -> notificationService.getBroadcastStatus(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_BROADCAST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("알림 읽음 처리")
    class MarkAsRead {

        @Test
        @DisplayName("내 알림을 읽음 처리할 수 있다")
        void markAsRead_success() {
            // given
            Notification notification = saveNotification(currentUser, "읽음 처리할 알림");
            entityManager.flush();
            entityManager.clear();

            // when
            notificationService.markAsRead(notification.getNotificationId());

            // then
            Notification updatedNotification = notificationRepository
                    .findById(notification.getNotificationId())
                    .orElseThrow();
            assertThat(updatedNotification.isRead()).isTrue();
        }

        @Test
        @DisplayName("다른 사용자의 알림은 읽음 처리할 수 없다")
        void markAsRead_otherUserNotification() {
            // given
            User otherUser = saveUser(900000000002L, "다른사용자");
            Notification notification = saveNotification(otherUser, "다른 사용자 알림");
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(notification.getNotificationId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 알림은 읽음 처리할 수 없다")
        void markAsRead_notificationNotFound() {
            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("알림 전체 읽음 처리")
    class MarkAllAsRead {

        @Test
        @DisplayName("내 읽지 않은 알림을 모두 읽음 처리할 수 있다")
        void markAllAsRead_success() {
            // given
            Notification firstNotification = saveNotification(currentUser, "첫 번째 알림");
            Notification secondNotification = saveNotification(currentUser, "두 번째 알림");
            Notification alreadyReadNotification = saveNotification(currentUser, "이미 읽은 알림");
            alreadyReadNotification.markRead();

            User otherUser = saveUser(900000000002L, "다른사용자");
            Notification otherUserNotification = saveNotification(otherUser, "다른 사용자 알림");

            entityManager.flush();
            entityManager.clear();

            // when
            notificationService.markAllAsRead();
            entityManager.flush();
            entityManager.clear();

            // then
            assertThat(notificationRepository.findById(firstNotification.getNotificationId()).orElseThrow().isRead())
                    .isTrue();
            assertThat(notificationRepository.findById(secondNotification.getNotificationId()).orElseThrow().isRead())
                    .isTrue();
            assertThat(notificationRepository.findById(alreadyReadNotification.getNotificationId()).orElseThrow().isRead())
                    .isTrue();
            assertThat(notificationRepository.findById(otherUserNotification.getNotificationId()).orElseThrow().isRead())
                    .isFalse();
            assertThat(notificationRepository.countByUser_UserIdAndReadFalse(currentUser.getUserId()))
                    .isZero();
            assertThat(notificationRepository.countByUser_UserIdAndReadFalse(otherUser.getUserId()))
                    .isEqualTo(1L);
        }
    }

    private User saveUser(Long kakaoId, String nickname) {
        return saveUser(kakaoId, nickname, UserRole.USER, UserStatus.ACTIVE);
    }

    private User saveUser(Long kakaoId, String nickname, UserRole role, UserStatus status) {
        return userRepository.save(User.builder()
                .region(region)
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(role)
                .status(status)
                .build());
    }

    private Notification saveNotification(User user, String content) {
        return notificationRepository.save(Notification.create(user, inquiryType, content));
    }

    private void updateCreatedAt(Notification notification, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE notification SET created_at = ?, modified_at = ? WHERE notification_id = ?",
                createdAt,
                createdAt,
                notification.getNotificationId()
        );
    }
}
