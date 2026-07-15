package com.sonbadrim_server.domain.search.service;

import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.search.dto.response.SearchEngineerListResponseDto;
import com.sonbadrim_server.domain.search.dto.response.SearchSuggestionResponseDto;
import com.sonbadrim_server.global.config.CacheConfig;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {
    private static final int MAX_FILTER_SELECTION = 5;
    private static final int DEFAULT_SUGGESTION_SIZE = 8;
    private static final int MAX_SUGGESTION_SIZE = 10;

    private final EngineerRepository engineerRepository;
    private final EngineerCategoryRepository engineerCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;

    /**
     * keyword + 필터링(category, region) 검색
     */
    @Cacheable(
            cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE,
            keyGenerator = "searchEngineerKeyGenerator",
            condition = "#pageable.pageNumber == 0"
    )
    public SearchEngineerListResponseDto searchEngineers(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedCity = normalizeKeyword(city);
        List<Integer> normalizedCategoryIds = normalizeList(categoryIds);
        String cityFilter = regionId != null ? null : normalizedCity;

        Slice<Engineer> engineerSlice = engineerRepository.searchEngineers(
                normalizedKeyword,
                normalizedCategoryIds,
                cityFilter,
                regionId,
                pageable
        );

        Map<Long, List<String>> categoryMap = getCategoryMap(
                engineerSlice.getContent().stream().map(Engineer::getEngineerId).toList()
        );

        List<EngineerSummaryResponseDto> items = engineerSlice.getContent().stream()
                .map(engineer -> EngineerSummaryResponseDto.builder()
                .engineerId(engineer.getEngineerId())
                .engineerName(engineer.getEngineerName())
                .profileImage(engineer.getProfileImage())
                .categories(categoryMap.getOrDefault(engineer.getEngineerId(), Collections.emptyList()))
                .introduction(truncateIntroduction(engineer.getIntroduction()))
                .build()
        )
                .toList();

        return new SearchEngineerListResponseDto(items, engineerSlice.hasNext());
    }

    /**
     * 사용자가 선택한 기술자와 연관성 높은 기술자를 추천해주기
     * RULE. 카테고리가 많이 겹친 순서 => 지역이 겹친 순서 => 최신으로 만들어진 순서
     */
    public SearchEngineerListResponseDto recommendEngineers(
            Long baseEngineerId,
            Pageable pageable
    ) {
        Integer baseRegionId = engineerRepository.findRegionIdByEngineerId(baseEngineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));
        List<Integer> baseCategoryIds = engineerCategoryRepository.findCategoryIdsByEngineerId(baseEngineerId);

        Slice<Engineer> engineerSlice = engineerRepository.findRecommendedEngineers(
                baseEngineerId,
                baseRegionId,
                baseCategoryIds,
                pageable
        );

        Map<Long, List<String>> categoryMap = getCategoryMap(
                engineerSlice.getContent().stream().map(Engineer::getEngineerId).toList()
        );

        List<EngineerSummaryResponseDto> items = engineerSlice.getContent().stream()
                .map(engineer -> EngineerSummaryResponseDto.builder()
                        .engineerId(engineer.getEngineerId())
                        .engineerName(engineer.getEngineerName())
                        .profileImage(engineer.getProfileImage())
                        .categories(categoryMap.getOrDefault(engineer.getEngineerId(), Collections.emptyList()))
                        .introduction(truncateIntroduction(engineer.getIntroduction()))
                        .build()
                )
                .toList();

        return new SearchEngineerListResponseDto(items, engineerSlice.hasNext());
    }

    /**
     * 검색 중 기술자를 제안해주기
     */
    public SearchSuggestionResponseDto getEngineerSuggestions(String keyword, Integer size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return new SearchSuggestionResponseDto(List.of());
        }

        int suggestionSize = normalizeSuggestionSize(size);
        PageRequest suggestionPageRequest = PageRequest.of(0, suggestionSize);

        List<String> engineerNames = engineerRepository.findEngineerNameSuggestions(normalizedKeyword, suggestionPageRequest);
        List<String> categoryNames = categoryRepository.findCategoryNameSuggestions(normalizedKeyword, suggestionPageRequest);
        List<String> cities = regionRepository.findCitySuggestions(normalizedKeyword, suggestionPageRequest);
        List<String> districts = regionRepository.findDistrictSuggestions(normalizedKeyword, suggestionPageRequest);

        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(engineerNames);
        merged.addAll(categoryNames);
        merged.addAll(cities);
        merged.addAll(districts);

        List<String> items = merged.stream()
                .limit(suggestionSize)
                .toList();

        return new SearchSuggestionResponseDto(items);
    }

    /**
     * 앞 뒤 공백을 없애주는 유틸 함수
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 겹치는 것을 없애고, 최대 개수 선택을 넘기는 지 체크
     */
    private List<Integer> normalizeList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Integer> normalized = values.stream().distinct().toList();
        if (normalized.size() > MAX_FILTER_SELECTION) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    /**
     *
     */
    private int normalizeSuggestionSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SUGGESTION_SIZE;
        }
        return Math.min(size, MAX_SUGGESTION_SIZE);
    }

    /**
     *
     * @param engineerIds (조건에 부합되게 select된 engineerIds)
     */
    private Map<Long, List<String>> getCategoryMap(List<Long> engineerIds) {
        if (engineerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EngineerCategory> engineerCategories = engineerCategoryRepository.findByEngineer_EngineerIdIn(engineerIds);
        return engineerCategories.stream()
                .collect(Collectors.groupingBy(
                        engineerCategory -> engineerCategory.getEngineer().getEngineerId(),
                        Collectors.mapping(
                                engineerCategory -> engineerCategory.getCategory().getCategoryName(),
                                Collectors.toList()
                        )
                ));
    }

    private String truncateIntroduction(String introduction) {
        if (introduction == null || introduction.isBlank()) {
            return "";
        }
        String trimmed = introduction.trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }
        return trimmed.substring(0, 100) + "...";
    }
}
