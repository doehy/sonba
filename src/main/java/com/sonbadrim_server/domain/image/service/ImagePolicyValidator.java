package com.sonbadrim_server.domain.image.service;

import com.sonbadrim_server.domain.image.entity.ImageFolderType;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

@Component
public class ImagePolicyValidator {

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp"
    );

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-domain:}")
    private String publicDomain;

    @Value("${cloudflare.r2.endpoint}")
    private String endpoint;

    public String validateUploadFile(String originalFileName, String contentType) {
        String extension = extractExtension(originalFileName);
        String expectedContentType = CONTENT_TYPE_BY_EXTENSION.get(extension);

        if (expectedContentType == null) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_CONTENT_TYPE);
        }
        if (!expectedContentType.equals(contentType)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_CONTENT_TYPE);
        }

        return extension;
    }

    public String validateProfileImageUrl(String imageUrl) {
        String normalizedUrl = trimToNull(imageUrl);
        if (normalizedUrl == null) {
            return null;
        }
        validateServiceImageUrl(normalizedUrl, ErrorCode.INVALID_PROFILE_IMAGE);
        return normalizedUrl;
    }

    public String validatePortfolioImageUrl(String imageUrl) {
        String normalizedUrl = trimToNull(imageUrl);
        if (normalizedUrl == null) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }
        validateServiceImageUrl(normalizedUrl, ErrorCode.INVALID_PORTFOLIO_IMAGE);
        return normalizedUrl;
    }

    private void validateServiceImageUrl(String imageUrl, ErrorCode errorCode) {
        URI uri = parseUri(imageUrl, errorCode);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new CustomException(errorCode);
        }

        String key = extractKey(imageUrl);
        if (key == null || !key.startsWith(ImageFolderType.ENGINEER.getFolder() + "/")) {
            throw new CustomException(errorCode);
        }

        String extension = extractExtension(key);
        if (!CONTENT_TYPE_BY_EXTENSION.containsKey(extension)) {
            throw new CustomException(errorCode);
        }
    }

    private URI parseUri(String imageUrl, ErrorCode errorCode) {
        try {
            URI uri = URI.create(imageUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new CustomException(errorCode);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new CustomException(errorCode);
        }
    }

    private String extractKey(String imageUrl) {
        String normalizedPublicDomain = trimTrailingSlash(publicDomain);
        if (!normalizedPublicDomain.isEmpty() && imageUrl.startsWith(normalizedPublicDomain + "/")) {
            return imageUrl.substring(normalizedPublicDomain.length() + 1);
        }

        String endpointPrefix = trimTrailingSlash(endpoint) + "/" + bucketName + "/";
        if (imageUrl.startsWith(endpointPrefix)) {
            return imageUrl.substring(endpointPrefix.length());
        }

        return null;
    }

    private String extractExtension(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        int lastSlashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        int lastDotIndex = trimmed.lastIndexOf('.');
        if (lastDotIndex <= lastSlashIndex || lastDotIndex == trimmed.length() - 1) {
            return "";
        }
        return trimmed.substring(lastDotIndex).toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
