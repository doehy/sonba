package com.sonbadrim_server.domain.user.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class AuthRateLimiter {

    private final boolean enabled;
    private final int maxRequests;
    private final Cache<String, AtomicInteger> attempts;

    public AuthRateLimiter(
            @Value("${app.auth-rate-limit.enabled:true}") boolean enabled,
            @Value("${app.auth-rate-limit.max-requests:30}") int maxRequests,
            @Value("${app.auth-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.auth-rate-limit.max-keys:10000}") long maxKeys
    ) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(maxKeys)
                .build();
    }

    public void check(HttpServletRequest request, String endpoint) {
        if (!enabled) {
            return;
        }

        String clientIp = resolveClientIp(request);
        String key = endpoint + ":" + clientIp;
        int requestCount = attempts.get(key, ignored -> new AtomicInteger()).incrementAndGet();

        if (requestCount > maxRequests) {
            log.warn("Auth rate limit exceeded. endpoint={}, clientIp={}, requestCount={}",
                    endpoint,
                    clientIp,
                    requestCount
            );
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
