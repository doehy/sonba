package com.sonbadream_server.domain.notification.repository;

import com.sonbadream_server.domain.notification.entity.NotificationBroadcast;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcast, Long> {

    // 중단됐거나 대기중인 공지 알림 찾기
    @Query("select nb.notificationBroadcastId from NotificationBroadcast nb where nb.status in (com.sonbadream_server.domain.notification.entity.NotificationBroadcastStatus.PENDING, com.sonbadream_server.domain.notification.entity.NotificationBroadcastStatus.PROCESSING)")
    java.util.List<Long> findInterruptedIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select nb
            from NotificationBroadcast nb
            where nb.notificationBroadcastId = :broadcastId
            """)
    Optional<NotificationBroadcast> findByIdForUpdate(Long broadcastId);
}
