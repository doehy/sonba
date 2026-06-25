package com.sonbadrim_server.domain.notification.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private final Map<Long, Map<String, SseEmitter>> emitterStore = new ConcurrentHashMap<>();

    public String add(Long userId, SseEmitter emitter) {
        String emitterId = UUID.randomUUID().toString();
        emitterStore.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);
        return emitterId;
    }

    public List<SseEmitterConnection> findByUserId(Long userId) {
        Map<String, SseEmitter> userEmitters = emitterStore.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return List.of();
        }

        return userEmitters.entrySet().stream()
                .map(entry -> new SseEmitterConnection(userId, entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<SseEmitterConnection> findAll() {
        return emitterStore.entrySet().stream()
                .flatMap(userEntry -> userEntry.getValue().entrySet().stream()
                        .map(emitterEntry -> new SseEmitterConnection(
                                userEntry.getKey(),
                                emitterEntry.getKey(),
                                emitterEntry.getValue()
                        )))
                .toList();
    }

    public void remove(Long userId, String emitterId) {
        Map<String, SseEmitter> userEmitters = emitterStore.get(userId);
        if (userEmitters == null) {
            return;
        }

        userEmitters.remove(emitterId);
        if (userEmitters.isEmpty()) {
            emitterStore.remove(userId);
        }
    }

    public boolean hasEmitter(Long userId) {
        return countByUserId(userId) > 0;
    }

    public int countByUserId(Long userId) {
        Map<String, SseEmitter> userEmitters = emitterStore.get(userId);
        return userEmitters == null ? 0 : userEmitters.size();
    }

    public record SseEmitterConnection(
            Long userId,
            String emitterId,
            SseEmitter emitter
    ) {
    }
}
