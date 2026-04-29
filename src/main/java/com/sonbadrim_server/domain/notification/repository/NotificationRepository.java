package com.sonbadrim_server.domain.notification.repository;

import com.sonbadrim_server.domain.notification.entity.Notification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Slice<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_UserIdAndReadFalse(Long userId);

    Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);

    @Modifying
    @Query("""
            update Notification n
            set n.read = true
            where n.user.userId = :userId
              and n.read = false
            """)
    int markAllAsRead(@Param("userId") Long userId);
}
