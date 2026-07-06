package com.sonbadrim_server.domain.notification.service;

import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationBroadcastWorker {

    private static final int BROADCAST_CHUNK_SIZE = 1_000;

    private final UserRepository userRepository;
    private final NotificationBroadcastProgressService notificationBroadcastProgressService;

    public void process(Long broadcastId) {
        try {
            NotificationBroadcastJobSnapshot job = notificationBroadcastProgressService.start(broadcastId);
            processChunks(broadcastId, job);
            notificationBroadcastProgressService.complete(broadcastId);
        } catch (Exception e) {
            notificationBroadcastProgressService.fail(broadcastId, e.getMessage());
        }
    }

    private void processChunks(Long broadcastId, NotificationBroadcastJobSnapshot job) {
        long lastUserId = 0L;
        Pageable chunkRequest = PageRequest.of(0, BROADCAST_CHUNK_SIZE);

        while (true) {
            List<Long> userIds = userRepository.findUserIdsByStatusAfterId(
                    UserStatus.ACTIVE,
                    lastUserId,
                    chunkRequest
            );

            if (userIds.isEmpty()) {
                return;
            }

            notificationBroadcastProgressService.saveChunk(
                    broadcastId,
                    userIds,
                    job.notificationTypeId(),
                    job.content()
            );
            lastUserId = userIds.getLast();
        }
    }
}
