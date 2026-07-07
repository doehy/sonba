package com.sonbadrim_server.domain.notification.repository;

import com.sonbadrim_server.domain.notification.entity.NotificationBroadcast;
import com.sonbadrim_server.domain.notification.entity.NotificationType;
import com.sonbadrim_server.domain.notification.entity.NotificationTypeCode;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/notification-test-data.sql")
class NotificationBulkRepositoryTest {

    @Autowired
    private NotificationBulkRepository notificationBulkRepository;

    @Autowired
    private NotificationBroadcastRepository notificationBroadcastRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationTypeRepository notificationTypeRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private UserRepository userRepository;

    private Region region;
    private NotificationType noticeType;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        noticeType = notificationTypeRepository.findByType(NotificationTypeCode.NOTICE)
                .orElseThrow();
    }

    @Nested
    @DisplayName("공지 알림 벌크 저장")
    class SaveNoticeNotifications {

        @Test
        @DisplayName("같은 공지 작업과 사용자는 한 번만 저장한다")
        void saveNoticeNotifications_ignoreDuplicatedBroadcastUser() {
            // given
            User firstUser = saveUser(900000000101L, "첫번째대상");
            User secondUser = saveUser(900000000102L, "두번째대상");
            NotificationBroadcast broadcast = notificationBroadcastRepository.save(
                    NotificationBroadcast.create(noticeType, "전체 공지입니다.", 2L)
            );
            List<Long> userIds = List.of(firstUser.getUserId(), secondUser.getUserId());

            // when
            int firstInsertedCount = notificationBulkRepository.saveNoticeNotifications(
                    broadcast.getNotificationBroadcastId(),
                    userIds,
                    noticeType.getNotificationTypeId(),
                    "전체 공지입니다."
            );
            int secondInsertedCount = notificationBulkRepository.saveNoticeNotifications(
                    broadcast.getNotificationBroadcastId(),
                    userIds,
                    noticeType.getNotificationTypeId(),
                    "전체 공지입니다."
            );

            // then
            assertThat(firstInsertedCount).isEqualTo(2);
            assertThat(secondInsertedCount).isZero();
            assertThat(notificationRepository.findAll()).hasSize(2);
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
