package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.notification.dto.response.NotificationSseResponseDto;
import com.sonbadrim_server.domain.notification.entity.Notification;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationSseService {

    private static final long DEFAULT_TIMEOUT_MS = 60L * 60 * 1000;

    private final Map<Long, Map<String, SseEmitter>> emitterStore = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        String emitterId = UUID.randomUUID().toString();

        emitterStore.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitterId));
        emitter.onTimeout(() -> removeEmitter(userId, emitterId));
        emitter.onError(ignored -> removeEmitter(userId, emitterId));

        sendEvent(emitter, "connect", "SSE connection established");
        return emitter;
    }

    public boolean sendNotification(Long userId, Notification notification) {
        Map<String, SseEmitter> userEmitters = emitterStore.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
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
        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            try {
                sendEvent(entry.getValue(), "notification", payload);
                delivered = true;
            } catch (Exception e) {
                removeEmitter(userId, entry.getKey());
            }
        }
        return delivered;
    }

    @Scheduled(fixedDelay = 30_000)
    public void sendHeartbeat() {
        for (Map.Entry<Long, Map<String, SseEmitter>> userEntry : emitterStore.entrySet()) {
            Long userId = userEntry.getKey();
            Map<String, SseEmitter> userEmitters = userEntry.getValue();

            for (Map.Entry<String, SseEmitter> emitterEntry : userEmitters.entrySet()) {
                try {
                    sendEvent(emitterEntry.getValue(), "heartbeat", "keepalive");
                } catch (Exception e) {
                    removeEmitter(userId, emitterEntry.getKey());
                }
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

    private void removeEmitter(Long userId, String emitterId) {
        Map<String, SseEmitter> userEmitters = emitterStore.get(userId);
        if (userEmitters == null) {
            return;
        }

        userEmitters.remove(emitterId);
        if (userEmitters.isEmpty()) {
            emitterStore.remove(userId);
        }
    }
}
