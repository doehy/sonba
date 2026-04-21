package com.sonbadrim_server.domain.search.service;

import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.search.dto.response.SearchEngineerListResponseDto;
import com.sonbadrim_server.domain.search.dto.response.SearchSuggestionResponseDto;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    /**
     * keyword + 필터링(category, region) 검색
     */
    public SearchEngineerListResponseDto searchEngineers(
            String keyword,
            List<Integer> categoryIds,
            Integer regionId,
            Pageable pageable
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        List<Integer> normalizedCategoryIds = normalizeList(categoryIds);

        Page<Engineer> engineerPage = engineerRepository.searchEngineers(
                normalizedKeyword,
                normalizedCategoryIds,
                regionId,
                pageable
        );

        Map<Long, List<String>> categoryMap = getCategoryMap(
                engineerPage.getContent().stream().map(Engineer::getEngineerId).toList()
        );

        List<EngineerSummaryResponseDto> items = engineerPage.getContent().stream()
                .map(engineer -> EngineerSummaryResponseDto.builder()
                .engineerId(engineer.getEngineerId())
                .headline(engineer.getHeadline())
                .profileImage(engineer.getProfileImage())
                .regionName(engineer.getRegion().getCity() + " " + engineer.getRegion().getDistrict())
                .categories(categoryMap.getOrDefault(engineer.getEngineerId(), Collections.emptyList()))
                .introduction(truncateIntroduction(engineer.getIntroduction()))
                .build()
        )
                .toList();

        return new SearchEngineerListResponseDto(items, engineerPage.hasNext());
    }

    /**
     * 사용자가 선택한 기술자와 연관성 높은 기술자를 추천해주기
     * RULE. 카테고리가 많이 겹친 순서 => 지역이 겹친 순서 => 최신으로 만들어진 순서
     */
    public SearchEngineerListResponseDto recommendEngineers(
            Long baseEngineerId,
            Pageable pageable
    ) {
        if (!engineerRepository.existsById(baseEngineerId)) {
            throw new CustomException(ErrorCode.ENGINEER_NOT_FOUND);
        }

        Slice<Engineer> engineerSlice = engineerRepository.findRecommendedEngineersByBaseEngineerId(
                baseEngineerId,
                pageable
        );

        Map<Long, List<String>> categoryMap = getCategoryMap(
                engineerSlice.getContent().stream().map(Engineer::getEngineerId).toList()
        );

        List<EngineerSummaryResponseDto> items = engineerSlice.getContent().stream()
                .map(engineer -> EngineerSummaryResponseDto.builder()
                        .engineerId(engineer.getEngineerId())
                        .headline(engineer.getHeadline())
                        .profileImage(engineer.getProfileImage())
                        .regionName(engineer.getRegion().getCity() + " " + engineer.getRegion().getDistrict())
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
        PageRequest pageRequest = PageRequest.of(0, suggestionSize);

        List<String> engineerNames = engineerRepository.findEngineerNameSuggestions(normalizedKeyword, pageRequest);
        List<String> headlines = engineerRepository.findHeadlineSuggestions(normalizedKeyword, pageRequest);

        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(engineerNames);
        merged.addAll(headlines);

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
