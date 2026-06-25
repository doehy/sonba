package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.notification.dto.response.NotificationSseResponseDto;
import com.sonbadrim_server.domain.notification.entity.Notification;
import com.sonbadrim_server.domain.notification.service.SseEmitterRegistry.SseEmitterConnection;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class NotificationSseService {

    private static final long DEFAULT_TIMEOUT_MS = 60L * 60 * 1000;

    private final SseEmitterRegistry sseEmitterRegistry;

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        String emitterId = sseEmitterRegistry.add(userId, emitter);

        emitter.onCompletion(() -> sseEmitterRegistry.remove(userId, emitterId));
        emitter.onTimeout(() -> sseEmitterRegistry.remove(userId, emitterId));
        emitter.onError(ignored -> sseEmitterRegistry.remove(userId, emitterId));

        sendEvent(emitter, "connect", "SSE connection established");
        return emitter;
    }

    public boolean sendNotificationEvent(Long userId, Notification notification) {
        var connections = sseEmitterRegistry.findByUserId(userId);
        if (connections.isEmpty()) {
            return false;
        }

        NotificationSseResponseDto payload = new NotificationSseResponseDto(
                notification.getNotificationId(),
                notification.getNotificationType().getType(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt()
        );

        boolean delivered = false;
        for (SseEmitterConnection connection : connections) {
            try {
                sendEvent(connection.emitter(), "notification", payload);
                delivered = true;
            } catch (Exception e) {
                sseEmitterRegistry.remove(connection.userId(), connection.emitterId());
            }
        }
        return delivered;
    }

    @Scheduled(fixedDelay = 30_000)
    public void sendHeartbeat() {
        for (SseEmitterConnection connection : sseEmitterRegistry.findAll()) {
            try {
                sendEvent(connection.emitter(), "heartbeat", "keepalive");
            } catch (Exception e) {
                sseEmitterRegistry.remove(connection.userId(), connection.emitterId());
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send SSE event", e);
        }
    }
}
