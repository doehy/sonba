package com.sonbadrim_server.domain.search.controller;

import com.sonbadrim_server.domain.search.dto.response.SearchEngineerListResponseDto;
import com.sonbadrim_server.domain.search.dto.response.SearchSuggestionResponseDto;
import com.sonbadrim_server.domain.search.service.SearchService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Search", description = "검색 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "기술자 검색", description = "키워드(engineerName/headline) + 지역/카테고리 필터로 기술자를 검색합니다.")
    @GetMapping("/engineers")
    public ResponseEntity<?> searchEngineers(
            @RequestParam(required = false) String keyword,
            @Size(max = 5, message = "카테고리는 최대 5개까지 선택 가능합니다.")
            @RequestParam(required = false) List<Integer> categoryIds,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer regionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        SearchEngineerListResponseDto response = searchService.searchEngineers(
                keyword,
                categoryIds,
                city,
                regionId,
                pageable
        );
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "기술자 추천", description = "선택한 기술자와 유사한 기술자를 추천합니다. (카테고리 겹침 개수 우선, 같은 지역 우선, 최신순)")
    @GetMapping("/engineers/{engineerId}/recommendations")
    public ResponseEntity<?> recommendEngineers(
            @PathVariable Long engineerId,
            @RequestParam(defaultValue = "0") int page,
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 20, message = "size는 20 이하여야 합니다.")
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        SearchEngineerListResponseDto response = searchService.recommendEngineers(
                engineerId,
                pageable
        );
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "검색 자동완성", description = "기술자 이름/짧은 소개(headline) 기반 자동완성 목록을 조회합니다.")
    @GetMapping("/engineers/suggestions")
    public ResponseEntity<?> getEngineerSuggestions(
            @RequestParam(required = false) String keyword,
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 10, message = "size는 10 이하여야 합니다.")
            @RequestParam(required = false) Integer size
    ) {
        SearchSuggestionResponseDto response = searchService.getEngineerSuggestions(keyword, size);
        return ResponseEntity.ok(CommonResponse.success(response));
    }
}
