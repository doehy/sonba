package com.sonbadrim_server.domain.notification.repository;

import com.sonbadrim_server.domain.notification.entity.NotificationType;
import com.sonbadrim_server.domain.notification.entity.NotificationTypeCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTypeRepository extends JpaRepository<NotificationType, Integer> {

    Optional<NotificationType> findByType(NotificationTypeCode type);
}
