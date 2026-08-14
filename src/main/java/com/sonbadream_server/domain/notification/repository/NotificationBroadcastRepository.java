package com.sonbadream_server.domain.notification.repository;

import com.sonbadream_server.domain.notification.entity.NotificationBroadcast;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcast, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select nb
            from NotificationBroadcast nb
            where nb.notificationBroadcastId = :broadcastId
            """)
    Optional<NotificationBroadcast> findByIdForUpdate(Long broadcastId);
}
