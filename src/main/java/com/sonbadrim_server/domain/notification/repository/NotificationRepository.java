package com.sonbadrim_server.domain.notification.repository;

import com.sonbadrim_server.domain.notification.entity.Notification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Slice<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUser_UserIdAndReadFalse(Long userId);

    Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);

    @Query("""
            select n
            from Notification n
            join fetch n.notificationType
            where n.notificationId = :notificationId
            """)
    Optional<Notification> findWithTypeByNotificationId(@Param("notificationId") Long notificationId);

    @Modifying
    @Query("""
            update Notification n
            set n.read = true
            where n.user.userId = :userId
              and n.read = false
            """)
    int markAllAsRead(@Param("userId") Long userId);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update Notification n
            set n.sseSent = true
            where n.notificationId = :notificationId
            """)
    int markSseSent(@Param("notificationId") Long notificationId);
}
