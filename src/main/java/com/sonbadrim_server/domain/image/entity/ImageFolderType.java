package com.sonbadrim_server.domain.image.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageFolderType {
    ENGINEER("engineer", "기술자 이미지"),
    REVIEW("review", "리뷰 이미지"),
    INQUIRY("inquiry", "문의 이미지");

    private final String folder;
    private final String description;

    public static ImageFolderType fromString(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        for (ImageFolderType imageFolderType : values()) {
            if (imageFolderType.name().equalsIgnoreCase(type)
                    || imageFolderType.getFolder().equalsIgnoreCase(type)) {
                return imageFolderType;
            }
        }
        return null;
    }
}