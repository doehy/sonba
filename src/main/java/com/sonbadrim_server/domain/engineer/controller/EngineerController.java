package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "기술자", description = "기술자 등록/조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/engineers")
public class EngineerController {

    private final EngineerService engineerService;

    @Operation(summary = "기술자 등록", description = "현재 로그인 사용자를 기술자로 등록합니다.")
    @PostMapping
    public ResponseEntity<?> registerEngineer(@Valid @RequestBody EngineerRequestDto request) {
        EngineerCreateResponse response = engineerService.registerEngineer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.success(response));
    }

    @Operation(summary = "기술자 수정", description = "현재 로그인 사용자의 기술자 정보를 수정합니다.")
    @PatchMapping("/{engineerId}")
    public ResponseEntity<?> updateEngineer(@PathVariable Long engineerId,
                                            @Valid @RequestBody EngineerRequestDto request) {
        engineerService.updateEngineer(engineerId, request);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "기술자 목록 조회", description = "기술자 목록을 페이지 단위로 조회합니다.")
    @GetMapping("/engineers")
    public ResponseEntity<?> getEngineerList(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<EngineerSummaryResponseDto> engineerList = engineerService.getEngineerList(pageable);

        return ResponseEntity.ok(CommonResponse.success(engineerList));
    }

    @Operation(summary = "기술자 상세 조회", description = "기술자 상세 정보를 조회합니다.")
    @GetMapping("/{engineerId}")
    public ResponseEntity<?> getEngineerDetail(@PathVariable Long engineerId) {
        EngineerDetailResponseDto response = engineerService.getEngineerDetail(engineerId);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "기술자 삭제", description = "현재 로그인한 사용자의 기술자 정보를 삭제합니다.")
    @DeleteMapping("/{engineerId}")
    public ResponseEntity<?> deleteEngineer(@PathVariable Long engineerId) {
        engineerService.deleteEngineer(engineerId);
        return ResponseEntity.ok(CommonResponse.success(null));

    }

}
