package com.sonbadrim_server.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SEARCH_ENGINEERS_CACHE = "searchEngineers";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SEARCH_ENGINEERS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(1))
                .recordStats());
        return cacheManager;
    }

    @Bean
    public KeyGenerator searchEngineerKeyGenerator() {
        return new SearchEngineerKeyGenerator();
    }

    private static class SearchEngineerKeyGenerator implements KeyGenerator {

        @Override
        public Object generate(Object target, Method method, Object... params) {
            String keyword = normalizeString((String) params[0]);
            List<?> categoryIds = normalizeCategoryIds((List<?>) params[1]);
            String city = normalizeString((String) params[2]);
            Integer regionId = (Integer) params[3];
            Pageable pageable = (Pageable) params[4];

            return String.join("|",
                    "keyword=" + keyword,
                    "categoryIds=" + categoryIds,
                    "city=" + city,
                    "regionId=" + regionId,
                    "page=" + pageable.getPageNumber(),
                    "size=" + pageable.getPageSize(),
                    "sort=" + pageable.getSort()
            );
        }

        private static String normalizeString(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? "" : trimmed.toLowerCase(Locale.ROOT);
        }

        private static List<String> normalizeCategoryIds(List<?> categoryIds) {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return List.of();
            }
            return categoryIds.stream()
                    .map(String::valueOf)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
