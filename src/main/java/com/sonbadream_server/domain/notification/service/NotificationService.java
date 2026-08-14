package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.dto.request.BroadcastNotificationRequestDto;
import com.sonbadream_server.domain.notification.dto.response.BroadcastNotificationStartResponseDto;
import com.sonbadream_server.domain.notification.dto.response.BroadcastNotificationStatusResponseDto;
import com.sonbadream_server.domain.notification.dto.response.NotificationItemResponseDto;
import com.sonbadream_server.domain.notification.dto.response.NotificationListResponseDto;
import com.sonbadream_server.domain.notification.entity.Notification;
import com.sonbadream_server.domain.notification.entity.NotificationBroadcast;
import com.sonbadream_server.domain.notification.entity.NotificationBroadcastStatus;
import com.sonbadream_server.domain.notification.entity.NotificationType;
import com.sonbadream_server.domain.notification.entity.NotificationTypeCode;
import com.sonbadream_server.domain.notification.event.NotificationBroadcastRequestedEvent;
import com.sonbadream_server.domain.notification.event.NotificationSseSendEvent;
import com.sonbadream_server.domain.notification.repository.NotificationBroadcastRepository;
import com.sonbadream_server.domain.notification.repository.NotificationRepository;
import com.sonbadream_server.domain.notification.repository.NotificationTypeRepository;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
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
    private final NotificationBroadcastRepository notificationBroadcastRepository;
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

    public BroadcastNotificationStartResponseDto startBroadcastNotice(BroadcastNotificationRequestDto requestDto) {
        User currentUser = userService.getCurrentUser();
        validateAdmin(currentUser);

        NotificationType notificationType = notificationTypeRepository.findByType(NotificationTypeCode.NOTICE)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));

        String content = normalizeContent(requestDto.getContent());
        long totalTargetCount = userRepository.countByStatus(UserStatus.ACTIVE);
        NotificationBroadcast broadcast = notificationBroadcastRepository.save(
                NotificationBroadcast.create(notificationType, content, totalTargetCount)
        );

        eventPublisher.publishEvent(new NotificationBroadcastRequestedEvent(broadcast.getNotificationBroadcastId()));
        return new BroadcastNotificationStartResponseDto(
                broadcast.getNotificationBroadcastId(),
                broadcast.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public BroadcastNotificationStatusResponseDto getBroadcastStatus(Long broadcastId) {
        User currentUser = userService.getCurrentUser();
        validateAdmin(currentUser);

        NotificationBroadcast broadcast = notificationBroadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_BROADCAST_NOT_FOUND));

        return new BroadcastNotificationStatusResponseDto(
                broadcast.getNotificationBroadcastId(),
                broadcast.getStatus(),
                broadcast.getTotalTargetCount(),
                broadcast.getProcessedCount(),
                broadcast.getFailedCount(),
                broadcast.getFailureReason(),
                broadcast.getCreatedAt(),
                broadcast.getModifiedAt()
        );
    }

    public BroadcastNotificationStartResponseDto retryBroadcastNotice(Long broadcastId) {
        User currentUser = userService.getCurrentUser();
        validateAdmin(currentUser);

        NotificationBroadcast broadcast = notificationBroadcastRepository.findByIdForUpdate(broadcastId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_BROADCAST_NOT_FOUND));

        if (broadcast.getStatus() != NotificationBroadcastStatus.FAILED) {
            throw new CustomException(ErrorCode.NOTIFICATION_BROADCAST_NOT_RETRYABLE);
        }

        broadcast.retry();
        eventPublisher.publishEvent(new NotificationBroadcastRequestedEvent(broadcast.getNotificationBroadcastId()));
        return new BroadcastNotificationStartResponseDto(
                broadcast.getNotificationBroadcastId(),
                broadcast.getStatus()
        );
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

    private void validateAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }
}
