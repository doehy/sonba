package com.sonbadream_server.domain.image.service;

import com.sonbadream_server.domain.image.dto.response.PresignedUrlResponseDto;
import com.sonbadream_server.domain.image.entity.ImageFolderType;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    private final S3Presigner s3Presigner;
    private final ImagePolicyValidator imagePolicyValidator;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

    @Value("${cloudflare.r2.presigned-expiration-seconds}")
    private long presignedExpirationSeconds;

    public PresignedUrlResponseDto generatePresignedUrlWithImageUrl(
            String imageFolderTypeStr,
            String originalFileName,
            String contentType,
            Long imageSize
    ) {
        ImageFolderType imageFolderType = validateImageFolderType(imageFolderTypeStr);
        String extension = imagePolicyValidator.validateUploadFile(originalFileName, contentType);
        validateImageSize(imageSize);

        String fileName = generateFileName(extension);
        String key = imageFolderType.getFolder() + "/" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedExpirationSeconds))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        String presignedUrl = presignedRequest.url().toString();
        String imageUrl = getImageUrl(key);

        log.info("Generated R2 presigned URL for key: {}", key);
        return new PresignedUrlResponseDto(presignedUrl, imageUrl);
    }

    private String getImageUrl(String key) {
        return String.format("%s/%s", trimTrailingSlash(publicDomain), key);
    }

    private String generateFileName(String extension) {
        return UUID.randomUUID() + extension;
    }

    private ImageFolderType validateImageFolderType(String imageFolderTypeStr) {
        ImageFolderType imageFolderType = ImageFolderType.fromString(imageFolderTypeStr);
        if (imageFolderType == null) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FOLDER_TYPE);
        }
        return imageFolderType;
    }

    private void validateImageSize(Long imageSize) {
        if (imageSize == null || imageSize <= 0) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_SIZE);
        }
        if (imageSize > MAX_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("cloudflare.r2.public-domain must not be blank");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
