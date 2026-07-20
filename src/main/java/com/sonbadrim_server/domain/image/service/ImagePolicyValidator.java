package com.sonbadrim_server.domain.image.service;

import com.sonbadrim_server.domain.image.entity.ImageFolderType;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImagePolicyValidator {

    private static final String IMAGE_HEADER_RANGE = "bytes=0-15";

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp"
    );

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

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

        validateMagicNumber(key, extension, errorCode);
    }

    private void validateMagicNumber(String key, String extension, ErrorCode errorCode) {
        byte[] header = getImageHeader(key, errorCode);
        boolean valid = switch (extension) {
            case ".jpg", ".jpeg" -> isJpeg(header);
            case ".png" -> isPng(header);
            case ".webp" -> isWebp(header);
            default -> false;
        };

        if (!valid) {
            throw new CustomException(errorCode);
        }
    }

    private byte[] getImageHeader(String key, ErrorCode errorCode) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .range(IMAGE_HEADER_RANGE)
                    .build();
            ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObject(request, ResponseTransformer.toBytes());
            return responseBytes.asByteArray();
        } catch (Exception e) {
            throw new CustomException(errorCode);
        }
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        return header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50;
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
