package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.event.NotificationBroadcastRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationBroadcastEventListener {

    private final NotificationBroadcastWorker notificationBroadcastWorker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationBroadcastRequestedEvent event) {
        notificationBroadcastWorker.process(event.broadcastId());
    }
}
