package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.entity.Notification;
import com.sonbadream_server.domain.notification.event.NotificationSseSendEvent;
import com.sonbadream_server.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseEventListener {

    private final NotificationRepository notificationRepository;
    private final NotificationSseService notificationSseService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationSseSendEvent event) {
        Notification notification = notificationRepository.findWithTypeByNotificationId(event.notificationId())
                .orElse(null);
        if (notification == null) {
            return;
        }

        boolean delivered = notificationSseService.sendNotificationEvent(event.userId(), notification);
        if (delivered) {
            notificationRepository.markSseSent(event.notificationId());
        }
    }
}
