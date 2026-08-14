package com.sonbadream_server.domain.region.controller;

import com.sonbadream_server.domain.region.dto.response.RegionResponseDto;
import com.sonbadream_server.domain.region.repository.RegionRepository;
import com.sonbadream_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Region", description = "지역 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/regions")
public class RegionController {

    private final RegionRepository regionRepository;

    @GetMapping
    @Operation(summary = "지역 목록 조회", description = "회원가입/필터에 사용하는 지역 목록을 조회합니다.")
    public ResponseEntity<?> getRegions() {
        List<RegionResponseDto> regions = regionRepository.findAllByOrderByRegionIdAsc().stream()
                .map(RegionResponseDto::from)
                .toList();
        return ResponseEntity.ok(CommonResponse.success(regions));
    }
}
