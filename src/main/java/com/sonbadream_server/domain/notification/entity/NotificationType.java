package com.sonbadream_server.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import com.sonbadream_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationType extends BaseTimeEntity {

    @Id
    @Column(name = "notification_type_id")
    private Integer notificationTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationTypeCode type;

    @Column(name = "template", nullable = false, length = 255)
    private String template;

    @OneToMany(mappedBy = "notificationType")
    private List<Notification> notifications = new ArrayList<>();
}
