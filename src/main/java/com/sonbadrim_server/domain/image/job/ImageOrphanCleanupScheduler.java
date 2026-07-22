package com.sonbadrim_server.domain.image.job;

import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageOrphanCleanupScheduler {

    private static final String ENGINEER_PREFIX = "engineer/";

    private final S3Client s3Client;
    private final EngineerRepository engineerRepository;
    private final PortfolioRepository portfolioRepository;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

    @Value("${image.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${image.cleanup.orphan-retention-hours:24}")
    private long orphanRetentionHours;

    @Scheduled(cron = "${image.cleanup.cron:0 30 3 * * *}")
    public void cleanupOrphanEngineerImages() {
        if (!cleanupEnabled) {
            return;
        }

        KeyCollectionResult keyCollectionResult = collectUsedKeys();
        if (keyCollectionResult.unresolvedUrlCount() > 0) {
            log.warn(
                    "Skip orphan cleanup: unresolved image URLs detected. unresolvedCount={}, sampledUrls={}",
                    keyCollectionResult.unresolvedUrlCount(),
                    keyCollectionResult.unresolvedSamples()
            );
            return;
        }
        Set<String> usedKeys = keyCollectionResult.usedKeys();
        Instant cutoff = Instant.now().minus(orphanRetentionHours, ChronoUnit.HOURS);

        int deletedCount = 0;
        int deleteFailedCount = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(ENGINEER_PREFIX)
                    .maxKeys(1000);

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response page = s3Client.listObjectsV2(requestBuilder.build());
            for (S3Object object : page.contents()) {
                String key = object.key();
                Instant lastModified = object.lastModified();

                if (usedKeys.contains(key)) {
                    continue;
                }
                if (lastModified == null || lastModified.isAfter(cutoff)) {
                    continue;
                }

                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build());
                    deletedCount++;
                } catch (Exception e) {
                    deleteFailedCount++;
                    log.warn("Failed to delete orphan image key={}", key, e);
                }
            }

            continuationToken = page.nextContinuationToken();
        } while (continuationToken != null);

        if (deletedCount > 0) {
            log.info("Deleted orphan engineer images count={}", deletedCount);
        }
        if (deleteFailedCount > 0) {
            log.warn("Failed to delete orphan engineer images count={}", deleteFailedCount);
        }
    }

    private KeyCollectionResult collectUsedKeys() {
        Set<String> keys = new HashSet<>();
        AtomicInteger unresolvedCount = new AtomicInteger();
        Set<String> unresolvedSamples = new HashSet<>();

        List<String> profileUrls = engineerRepository.findAllUsedProfileImageUrls();
        List<String> profileThumbnailUrls = engineerRepository.findAllUsedProfileThumbnailImageUrls();
        List<String> portfolioUrls = portfolioRepository.findAllUsedPortfolioImageUrls();
        List<String> portfolioThumbnailUrls = portfolioRepository.findAllUsedPortfolioThumbnailImageUrls();

        for (String url : profileUrls) {
            String key = extractKey(url);
            if (key != null) {
                keys.add(key);
            } else {
                unresolvedCount.incrementAndGet();
                if (unresolvedSamples.size() < 5) {
                    unresolvedSamples.add(url);
                }
            }
        }
        for (String url : profileThumbnailUrls) {
            String key = extractKey(url);
            if (key != null) {
                keys.add(key);
            } else {
                unresolvedCount.incrementAndGet();
                if (unresolvedSamples.size() < 5) {
                    unresolvedSamples.add(url);
                }
            }
        }
        for (String url : portfolioUrls) {
            String key = extractKey(url);
            if (key != null) {
                keys.add(key);
            } else {
                unresolvedCount.incrementAndGet();
                if (unresolvedSamples.size() < 5) {
                    unresolvedSamples.add(url);
                }
            }
        }
        for (String url : portfolioThumbnailUrls) {
            String key = extractKey(url);
            if (key != null) {
                keys.add(key);
            } else {
                unresolvedCount.incrementAndGet();
                if (unresolvedSamples.size() < 5) {
                    unresolvedSamples.add(url);
                }
            }
        }

        return new KeyCollectionResult(keys, unresolvedCount.get(), unresolvedSamples);
    }

    private String extractKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalizedPublic = trimTrailingSlash(publicDomain);
        if (!normalizedPublic.isEmpty() && url.startsWith(normalizedPublic + "/")) {
            return url.substring(normalizedPublic.length() + 1);
        }

        return null;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record KeyCollectionResult(
            Set<String> usedKeys,
            int unresolvedUrlCount,
            Set<String> unresolvedSamples
    ) {
    }
}
