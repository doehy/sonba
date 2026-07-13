package com.sonbadrim_server.domain.search.service;

import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.search.dto.response.SearchEngineerListResponseDto;
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
}
