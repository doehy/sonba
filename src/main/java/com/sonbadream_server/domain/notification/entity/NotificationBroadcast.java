package com.sonbadream_server.domain.notification.entity;

import com.sonbadream_server.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification_broadcast")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationBroadcast extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_broadcast_id")
    private Long notificationBroadcastId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_type_id", nullable = false)
    private NotificationType notificationType;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationBroadcastStatus status;

    @Column(name = "total_target_count", nullable = false)
    private long totalTargetCount;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "last_processed_user_id", nullable = false)
    private long lastProcessedUserId;

    @Column(name = "failed_count", nullable = false)
    private long failedCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public static NotificationBroadcast create(
            NotificationType notificationType,
            String content,
            long totalTargetCount
    ) {
        NotificationBroadcast broadcast = new NotificationBroadcast();
        broadcast.notificationType = notificationType;
        broadcast.content = content;
        broadcast.status = NotificationBroadcastStatus.PENDING;
        broadcast.totalTargetCount = totalTargetCount;
        broadcast.processedCount = 0L;
        broadcast.lastProcessedUserId = 0L;
        broadcast.failedCount = 0L;
        return broadcast;
    }

    public void start() {
        this.status = NotificationBroadcastStatus.PROCESSING;
        this.failureReason = null;
    }

    public void recordProcessedChunk(long count, long lastProcessedUserId) {
        this.processedCount += count;
        this.lastProcessedUserId = lastProcessedUserId;
    }

    public void complete() {
        this.status = NotificationBroadcastStatus.COMPLETED;
        this.failedCount = 0L;
        this.failureReason = null;
    }

    public void fail(String reason) {
        this.status = NotificationBroadcastStatus.FAILED;
        this.failureReason = reason;
        this.failedCount = Math.max(totalTargetCount - processedCount, 0L);
    }

    public void retry() {
        this.status = NotificationBroadcastStatus.PENDING;
        this.failureReason = null;
        this.failedCount = Math.max(totalTargetCount - processedCount, 0L);
    }
}
