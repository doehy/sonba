package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.entity.NotificationBroadcast;
import com.sonbadream_server.domain.notification.entity.NotificationBroadcastStatus;
import com.sonbadream_server.domain.notification.repository.NotificationBroadcastRepository;
import com.sonbadream_server.domain.notification.repository.NotificationBulkRepository;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationBroadcastProgressService {

    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final NotificationBroadcastRepository notificationBroadcastRepository;
    private final NotificationBulkRepository notificationBulkRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationBroadcastJobSnapshot> start(Long broadcastId) {
        NotificationBroadcast broadcast = findBroadcastForUpdate(broadcastId);
        if (broadcast.getStatus() != NotificationBroadcastStatus.PENDING) {
            return Optional.empty();
        }
        broadcast.start();
        return Optional.of(new NotificationBroadcastJobSnapshot(
                broadcast.getNotificationType().getNotificationTypeId(),
                broadcast.getContent(),
                broadcast.getLastProcessedUserId()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChunk(
            Long broadcastId,
            List<Long> userIds,
            Integer notificationTypeId,
            String content
    ) {
        int insertedCount = notificationBulkRepository.saveNoticeNotifications(
                broadcastId,
                userIds,
                notificationTypeId,
                content
        );
        NotificationBroadcast broadcast = findBroadcastForUpdate(broadcastId);
        broadcast.recordProcessedChunk(insertedCount, userIds.getLast());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long broadcastId) {
        NotificationBroadcast broadcast = findBroadcastForUpdate(broadcastId);
        broadcast.complete();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long broadcastId, String reason) {
        NotificationBroadcast broadcast = findBroadcastForUpdate(broadcastId);
        if (broadcast.getStatus() == NotificationBroadcastStatus.COMPLETED) {
            return;
        }
        broadcast.fail(truncate(reason));
    }

    private NotificationBroadcast findBroadcastForUpdate(Long broadcastId) {
        return notificationBroadcastRepository.findByIdForUpdate(broadcastId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_BROADCAST_NOT_FOUND));
    }

    private String truncate(String reason) {
        String normalized = reason == null || reason.isBlank()
                ? "알림 발송 중 알 수 없는 오류가 발생했습니다."
                : reason.trim();
        if (normalized.length() <= MAX_FAILURE_REASON_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
