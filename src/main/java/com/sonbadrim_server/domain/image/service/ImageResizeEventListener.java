package com.sonbadrim_server.domain.image.service;

import com.sonbadrim_server.domain.image.event.ProfileImageUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ImageResizeEventListener {

    private final ImageResizeService imageResizeService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProfileImageUploadedEvent event) {
        imageResizeService.createProfileThumbnail(event.engineerId(), event.profileImageUrl());
    }
}
