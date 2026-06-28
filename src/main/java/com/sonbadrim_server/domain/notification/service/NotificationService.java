package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.notification.dto.response.NotificationItemResponseDto;
import com.sonbadrim_server.domain.notification.dto.response.NotificationListResponseDto;
import com.sonbadrim_server.domain.notification.entity.Notification;
import com.sonbadrim_server.domain.notification.entity.NotificationType;
import com.sonbadrim_server.domain.notification.entity.NotificationTypeCode;
import com.sonbadrim_server.domain.notification.event.NotificationSseSendEvent;
import com.sonbadrim_server.domain.notification.repository.NotificationRepository;
import com.sonbadrim_server.domain.notification.repository.NotificationTypeRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final int MAX_CONTENT_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final NotificationTypeRepository notificationTypeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public NotificationListResponseDto getMyNotifications(Pageable pageable) {
        User currentUser = userService.getCurrentUser();

        Slice<Notification> notificationSlice = notificationRepository
                .findByUser_UserIdOrderByCreatedAtDesc(currentUser.getUserId(), pageable);

        List<NotificationItemResponseDto> items = notificationSlice.getContent().stream()
                .map(notification -> new NotificationItemResponseDto(
                        notification.getNotificationId(),
                        notification.getNotificationType().getType(),
                        notification.getContent(),
                        notification.isRead(),
                        notification.getCreatedAt()
                ))
                .toList();

        long unreadCount = notificationRepository.countByUser_UserIdAndReadFalse(currentUser.getUserId());
        return new NotificationListResponseDto(items, notificationSlice.hasNext(), unreadCount);
    }

    public void markAsRead(Long notificationId) {
        User currentUser = userService.getCurrentUser();

        Notification notification = notificationRepository
                .findByNotificationIdAndUser_UserId(notificationId, currentUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markRead();
    }

    public void markAllAsRead() {
        User currentUser = userService.getCurrentUser();
        notificationRepository.markAllAsRead(currentUser.getUserId());
    }

    public void createInquiryNotification(Long userId, String senderDisplayName) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        NotificationType notificationType = notificationTypeRepository.findByType(NotificationTypeCode.INQUIRY)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));

        String content = renderTemplate(notificationType, senderDisplayName);
        Notification notification = notificationRepository.save(Notification.create(targetUser, notificationType, content));

        eventPublisher.publishEvent(new NotificationSseSendEvent(
                targetUser.getUserId(),
                notification.getNotificationId()
        ));
    }

    private String renderTemplate(NotificationType notificationType, String senderDisplayName) {
        String template = notificationType.getTemplate();
        String sender = senderDisplayName == null ? "알 수 없는 사용자" : senderDisplayName.trim();

        String rendered;
        if (template == null || template.isBlank()) {
            rendered = "새 문의가 도착했습니다.";
        } else if (template.contains("%s")) {
            rendered = String.format(template, sender);
        } else {
            rendered = template;
        }

        return rendered.trim();
    }
}
