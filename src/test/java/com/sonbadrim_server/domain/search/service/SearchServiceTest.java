package com.sonbadrim_server.domain.search.service;

import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.search.dto.response.SearchEngineerListResponseDto;
import com.sonbadrim_server.domain.search.dto.response.SearchSuggestionResponseDto;
import com.sonbadrim_server.global.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class SearchServiceTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private EngineerRepository engineerRepository;

    @MockitoBean
    private EngineerCategoryRepository engineerCategoryRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private RegionRepository regionRepository;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache(CacheConfig.SEARCH_ENGINEERS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Nested
    @DisplayName("기술자 검색 캐시")
    class SearchEngineersCache {

        @Test
        @DisplayName("첫 페이지의 같은 검색 조건은 캐시되어 repository를 한 번만 호출한다")
        void searchEngineers_cacheFirstPage() {
            // given
            PageRequest pageable = PageRequest.of(0, 20);
            given(engineerRepository.searchEngineers(
                    eq("책임감"),
                    eq(List.of(1, 2, 3)),
                    eq("서울특별시"),
                    eq(null),
                    eq(pageable)
            )).willReturn(new SliceImpl<>(List.of(), pageable, false));

            // when
            SearchEngineerListResponseDto first = searchService.searchEngineers(
                    "책임감",
                    List.of(1, 2, 3),
                    "서울특별시",
                    null,
                    pageable
            );
            SearchEngineerListResponseDto second = searchService.searchEngineers(
                    "책임감",
                    List.of(1, 2, 3),
                    "서울특별시",
                    null,
                    pageable
            );

            // then
            assertThat(first).isSameAs(second);
            assertThat(first.getItems()).isEmpty();
            assertThat(first.isHasNext()).isFalse();

            verify(engineerRepository, times(1)).searchEngineers(
                    eq("책임감"),
                    eq(List.of(1, 2, 3)),
                    eq("서울특별시"),
                    eq(null),
                    eq(pageable)
            );
            verify(engineerCategoryRepository, never()).findByEngineer_EngineerIdIn(any());
        }

        @Test
        @DisplayName("두 번째 페이지 이후는 캐시하지 않는다")
        void searchEngineers_doNotCacheAfterFirstPage() {
            // given
            PageRequest pageable = PageRequest.of(1, 20);
            given(engineerRepository.searchEngineers(
                    eq("책임감"),
                    eq(List.of(1, 2, 3)),
                    eq("서울특별시"),
                    eq(null),
                    eq(pageable)
            )).willReturn(new SliceImpl<>(List.of(), pageable, false));

            // when
            searchService.searchEngineers(
                    "책임감",
                    List.of(1, 2, 3),
                    "서울특별시",
                    null,
                    pageable
            );
            searchService.searchEngineers(
                    "책임감",
                    List.of(1, 2, 3),
                    "서울특별시",
                    null,
                    pageable
            );

            // then
            verify(engineerRepository, times(2)).searchEngineers(
                    eq("책임감"),
                    eq(List.of(1, 2, 3)),
                    eq("서울특별시"),
                    eq(null),
                    eq(pageable)
            );
        }
    }

    @Nested
    @DisplayName("검색 자동완성")
    class EngineerSuggestions {

        @Test
        @DisplayName("headline은 자동완성 후보에 포함하지 않는다")
        void getEngineerSuggestions_excludeHeadline() {
            // given
            given(engineerRepository.findEngineerNameSuggestions(eq("안녕하세"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(categoryRepository.findCategoryNameSuggestions(eq("안녕하세"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(regionRepository.findCitySuggestions(eq("안녕하세"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(regionRepository.findDistrictSuggestions(eq("안녕하세"), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SearchSuggestionResponseDto result = searchService.getEngineerSuggestions("안녕하세", 5);

            // then
            assertThat(result.getItems()).isEmpty();
        }

        @Test
        @DisplayName("기술자명, 카테고리명, 지역명을 중복 없이 순서대로 제안한다")
        void getEngineerSuggestions_mergeNameCategoryAndRegion() {
            // given
            given(engineerRepository.findEngineerNameSuggestions(eq("도배"), any(PageRequest.class)))
                    .willReturn(List.of("도배장인001", "도배장인002"));
            given(categoryRepository.findCategoryNameSuggestions(eq("도배"), any(PageRequest.class)))
                    .willReturn(List.of("도배"));
            given(regionRepository.findCitySuggestions(eq("도배"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(regionRepository.findDistrictSuggestions(eq("도배"), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SearchSuggestionResponseDto result = searchService.getEngineerSuggestions("도배", 5);

            // then
            assertThat(result.getItems()).containsExactly("도배장인001", "도배장인002", "도배");
        }

        @Test
        @DisplayName("지역 검색어는 시/구 후보를 제안한다")
        void getEngineerSuggestions_includeRegion() {
            // given
            given(engineerRepository.findEngineerNameSuggestions(eq("강남"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(categoryRepository.findCategoryNameSuggestions(eq("강남"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(regionRepository.findCitySuggestions(eq("강남"), any(PageRequest.class)))
                    .willReturn(List.of());
            given(regionRepository.findDistrictSuggestions(eq("강남"), any(PageRequest.class)))
                    .willReturn(List.of("강남구"));

            // when
            SearchSuggestionResponseDto result = searchService.getEngineerSuggestions("강남", 5);

            // then
            assertThat(result.getItems()).containsExactly("강남구");
        }
    }
}
