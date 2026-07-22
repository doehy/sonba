package com.sonbadrim_server.domain.image.service;

import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageResizeService {

    private static final int PROFILE_THUMBNAIL_MAX_WIDTH = 600;
    private static final int PROFILE_THUMBNAIL_MAX_HEIGHT = 600;
    private static final int PORTFOLIO_THUMBNAIL_MAX_WIDTH = 800;
    private static final int PORTFOLIO_THUMBNAIL_MAX_HEIGHT = 800;
    private static final String ENGINEER_PREFIX = "engineer/";
    private static final String PROFILE_THUMBNAIL_PREFIX = "engineer/thumbnail/";
    private static final String PORTFOLIO_THUMBNAIL_PREFIX = "engineer/portfolio-thumbnail/";
    private static final String THUMBNAIL_FORMAT = "jpg";
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";

    private final S3Client s3Client;
    private final EngineerRepository engineerRepository;
    private final PortfolioRepository portfolioRepository;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

    /**
     * 기술자 프로필 원본 이미지를 읽어 JPG 썸네일을 생성하고, 생성된 썸네일 URL을 기술자 정보에 저장한다.
     */
    @Transactional
    public void createProfileThumbnail(Long engineerId, String profileImageUrl) {
        if (engineerId == null || profileImageUrl == null || profileImageUrl.isBlank()) {
            return;
        }

        String originalKey = extractKey(profileImageUrl);
        if (originalKey == null || !originalKey.startsWith(ENGINEER_PREFIX)) {
            return;
        }

        if (!isSupportedOriginalExtension(originalKey)) {
            log.warn("Skip profile thumbnail: unsupported extension. engineerId={}, key={}", engineerId, originalKey);
            return;
        }

        try {
            byte[] resizedBytes = createThumbnailBytes(
                    originalKey,
                    PROFILE_THUMBNAIL_MAX_WIDTH,
                    PROFILE_THUMBNAIL_MAX_HEIGHT
            );
            if (resizedBytes == null) {
                log.warn("Skip profile thumbnail: unsupported image content. engineerId={}, key={}", engineerId, originalKey);
                return;
            }
            String thumbnailKey = toThumbnailKey(originalKey);

            putThumbnail(thumbnailKey, resizedBytes);

            String thumbnailUrl = getImageUrl(thumbnailKey);
            engineerRepository.updateProfileThumbnailImageIfProfileUnchanged(
                    engineerId,
                    profileImageUrl,
                    thumbnailUrl
            );
        } catch (Exception e) {
            log.warn("Failed to create profile thumbnail. engineerId={}, key={}", engineerId, originalKey, e);
        }
    }

    /**
     * 포트폴리오 원본 이미지를 읽어 JPG 썸네일을 생성하고, 생성된 썸네일 URL을 포트폴리오 정보에 저장한다.
     */
    @Transactional
    public void createPortfolioThumbnail(Long portfolioId, String portfolioImageUrl) {
        if (portfolioId == null || portfolioImageUrl == null || portfolioImageUrl.isBlank()) {
            return;
        }

        String originalKey = extractKey(portfolioImageUrl);
        if (originalKey == null || !originalKey.startsWith(ENGINEER_PREFIX)) {
            return;
        }

        if (!isSupportedOriginalExtension(originalKey)) {
            log.warn("Skip portfolio thumbnail: unsupported extension. portfolioId={}, key={}", portfolioId, originalKey);
            return;
        }

        try {
            byte[] resizedBytes = createThumbnailBytes(
                    originalKey,
                    PORTFOLIO_THUMBNAIL_MAX_WIDTH,
                    PORTFOLIO_THUMBNAIL_MAX_HEIGHT
            );
            if (resizedBytes == null) {
                log.warn("Skip portfolio thumbnail: unsupported image content. portfolioId={}, key={}", portfolioId, originalKey);
                return;
            }
            String thumbnailKey = toPortfolioThumbnailKey(originalKey);

            putThumbnail(thumbnailKey, resizedBytes);

            String thumbnailUrl = getImageUrl(thumbnailKey);
            portfolioRepository.updatePortfolioThumbnailImageIfPortfolioUnchanged(
                    portfolioId,
                    portfolioImageUrl,
                    thumbnailUrl
            );
        } catch (Exception e) {
            log.warn("Failed to create portfolio thumbnail. portfolioId={}, key={}", portfolioId, originalKey, e);
        }
    }

    /**
     * 원본 이미지를 읽고 지정한 최대 크기 안으로 축소한 JPG 썸네일 바이트 배열을 만든다.
     */
    private byte[] createThumbnailBytes(String originalKey, int maxWidth, int maxHeight) {
        BufferedImage originalImage = readImage(originalKey);
        if (originalImage == null) {
            return null;
        }

        BufferedImage resizedImage = resize(originalImage, maxWidth, maxHeight);
        return writeImage(toRgbImage(resizedImage), THUMBNAIL_FORMAT);
    }

    /**
     * 생성된 썸네일 바이트 배열을 R2에 JPEG 객체로 업로드한다.
     */
    private void putThumbnail(String thumbnailKey, byte[] resizedBytes) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(thumbnailKey)
                        .contentType(THUMBNAIL_CONTENT_TYPE)
                        .build(),
                RequestBody.fromBytes(resizedBytes)
        );
    }

    /**
     * R2에 저장된 이미지 객체를 key로 조회한 뒤 Java 이미지 객체로 변환한다.
     */
    private BufferedImage readImage(String key) {
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build(),
                ResponseTransformer.toBytes()
        );
        return readImage(responseBytes.asByteArray());
    }

    /**
     * 이미지 바이트 배열을 BufferedImage로 읽고, 읽을 수 없는 이미지면 null을 반환한다.
     */
    private BufferedImage readImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 원본 비율을 유지하면서 이미지가 지정한 최대 가로/세로 크기를 넘지 않도록 축소한다.
     */
    private BufferedImage resize(BufferedImage originalImage, int maxWidth, int maxHeight) {
        double ratio = Math.min(
                (double) maxWidth / originalImage.getWidth(),
                (double) maxHeight / originalImage.getHeight()
        );
        if (ratio >= 1.0) {
            return originalImage;
        }

        int targetWidth = Math.max(1, (int) Math.round(originalImage.getWidth() * ratio));
        int targetHeight = Math.max(1, (int) Math.round(originalImage.getHeight() * ratio));
        int imageType = originalImage.getTransparency() == BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resizedImage;
    }

    /**
     * JPG 저장을 위해 투명 배경을 흰색으로 합성한 RGB 이미지로 변환한다.
     */
    private BufferedImage toRgbImage(BufferedImage image) {
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    /**
     * BufferedImage를 지정한 이미지 포맷의 바이트 배열로 인코딩한다.
     */
    private byte[] writeImage(BufferedImage image, String formatName) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            if (!ImageIO.write(image, formatName, outputStream)) {
                throw new IllegalStateException("Unsupported image format: " + formatName);
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write resized image", e);
        }
    }

    /**
     * 썸네일 생성 대상으로 허용하는 원본 이미지 확장자인지 확인한다.
     */
    private boolean isSupportedOriginalExtension(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.endsWith(".jpg")
                || lowerKey.endsWith(".jpeg")
                || lowerKey.endsWith(".png")
                || lowerKey.endsWith(".webp");
    }

    /**
     * 원본 이미지 key를 프로필 썸네일 저장용 key로 변환한다.
     */
    private String toThumbnailKey(String originalKey) {
        return toThumbnailKey(originalKey, PROFILE_THUMBNAIL_PREFIX);
    }

    /**
     * 원본 이미지 key를 포트폴리오 썸네일 저장용 key로 변환한다.
     */
    private String toPortfolioThumbnailKey(String originalKey) {
        return toThumbnailKey(originalKey, PORTFOLIO_THUMBNAIL_PREFIX);
    }

    /**
     * 원본 이미지 key를 지정한 썸네일 prefix 아래의 JPG key로 변환한다.
     */
    private String toThumbnailKey(String originalKey, String thumbnailPrefix) {
        String fileName = originalKey.substring(ENGINEER_PREFIX.length());
        int lastDotIndex = fileName.lastIndexOf('.');
        String baseName = lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
        return thumbnailPrefix + baseName + "." + THUMBNAIL_FORMAT;
    }

    /**
     * R2 object key를 서비스에서 사용하는 public image URL로 변환한다.
     */
    private String getImageUrl(String key) {
        return trimTrailingSlash(publicDomain) + "/" + key;
    }

    /**
     * 서비스 public image URL에서 R2 object key를 추출한다.
     */
    private String extractKey(String imageUrl) {
        String normalizedPublicDomain = trimTrailingSlash(publicDomain);
        if (!normalizedPublicDomain.isEmpty() && imageUrl.startsWith(normalizedPublicDomain + "/")) {
            return imageUrl.substring(normalizedPublicDomain.length() + 1);
        }
        return null;
    }

    /**
     * URL 조합이 안정적으로 되도록 문자열 끝의 슬래시를 제거한다.
     */
    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
