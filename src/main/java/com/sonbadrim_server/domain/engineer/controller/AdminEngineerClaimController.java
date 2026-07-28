package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.response.EngineerClaimRequestResponseDto;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 기술자 프로필 연결 API", description = "관리자 기술자 프로필 연결 요청 처리 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/engineer-claims")
public class AdminEngineerClaimController {

    private final EngineerService engineerService;

    @Operation(summary = "기술자 프로필 연결 요청 목록 조회", description = "관리자가 처리해야 할 기술자 프로필 연결 요청 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<CommonResponse<Page<EngineerClaimRequestResponseDto>>> getEngineerClaimRequests(
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EngineerClaimRequestResponseDto> response = engineerService.getEngineerClaimRequests(pageable);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "기술자 프로필 연결 요청 승인", description = "관리자가 기술자 프로필 연결 요청을 승인합니다.")
    @PostMapping("/{engineerId}/approve")
    public ResponseEntity<CommonResponse<Void>> approveEngineerClaim(@PathVariable Long engineerId) {
        engineerService.approveEngineerClaim(engineerId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "기술자 프로필 연결 요청 거절", description = "관리자가 기술자 프로필 연결 요청을 거절합니다.")
    @PostMapping("/{engineerId}/reject")
    public ResponseEntity<CommonResponse<Void>> rejectEngineerClaim(@PathVariable Long engineerId) {
        engineerService.rejectEngineerClaim(engineerId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
