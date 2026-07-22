package com.sonbadrim_server.domain.image.event;

import com.sonbadrim_server.domain.image.entity.ImageUsageType;

public record ImageUploadedEvent(
        ImageUsageType usageType,
        Long ownerId,
        String imageUrl
) {
}
