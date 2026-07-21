package com.sonbadrim_server.domain.image.event;

public record ProfileImageUploadedEvent(
        Long engineerId,
        String profileImageUrl
) {
}
