package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.notification.entity.NotificationBroadcast;
import com.sonbadrim_server.domain.notification.repository.NotificationBroadcastRepository;
import com.sonbadrim_server.domain.notification.repository.NotificationBulkRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import java.util.List;
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
    public NotificationBroadcastJobSnapshot start(Long broadcastId) {
        NotificationBroadcast broadcast = findBroadcast(broadcastId);
        broadcast.start();
        return new NotificationBroadcastJobSnapshot(
                broadcast.getNotificationType().getNotificationTypeId(),
                broadcast.getContent()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChunk(
            Long broadcastId,
            List<Long> userIds,
            Integer notificationTypeId,
            String content
    ) {
        notificationBulkRepository.saveNoticeNotifications(userIds, notificationTypeId, content);
        NotificationBroadcast broadcast = findBroadcast(broadcastId);
        broadcast.increaseProcessedCount(userIds.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long broadcastId) {
        NotificationBroadcast broadcast = findBroadcast(broadcastId);
        broadcast.complete();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long broadcastId, String reason) {
        NotificationBroadcast broadcast = findBroadcast(broadcastId);
        broadcast.fail(truncate(reason));
    }

    private NotificationBroadcast findBroadcast(Long broadcastId) {
        return notificationBroadcastRepository.findById(broadcastId)
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
