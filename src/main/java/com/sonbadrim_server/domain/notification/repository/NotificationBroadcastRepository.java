package com.sonbadrim_server.domain.notification.repository;

import com.sonbadrim_server.domain.notification.entity.NotificationBroadcast;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcast, Long> {
}
