package com.sonbadrim_server.domain.notification.entity;

import com.sonbadrim_server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.sonbadrim_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_type_id", nullable = false)
    private NotificationType notificationType;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "sse_sent", nullable = false)
    private boolean sseSent;

    public static Notification create(User user, NotificationType notificationType, String content) {
        Notification notification = new Notification();
        notification.user = user;
        notification.notificationType = notificationType;
        notification.content = content;
        notification.read = false;
        notification.sseSent = false;
        return notification;
    }

    public void markRead() {
        this.read = true;
    }

    public void markSseSent() {
        this.sseSent = true;
    }
}
