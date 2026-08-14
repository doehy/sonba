package com.sonbadream_server.domain.image.service;

import com.sonbadream_server.domain.image.event.ImageUploadedEvent;
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
    public void handle(ImageUploadedEvent event) {
        switch (event.usageType()) {
            case ENGINEER_PROFILE -> imageResizeService.createProfileThumbnail(event.ownerId(), event.imageUrl());
            case ENGINEER_PORTFOLIO -> imageResizeService.createPortfolioThumbnail(event.ownerId(), event.imageUrl());
        }
    }
}
