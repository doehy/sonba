package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 기술자 API", description = "관리자 기술자 프로필 관리 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/engineers")
public class AdminEngineerController {

    private final EngineerService engineerService;

    @Operation(summary = "관리자 기술자 등록", description = "관리자가 수동으로 기술자 프로필을 등록합니다.")
    @PostMapping
    public ResponseEntity<?> registerAdminCreatedEngineer(
            @Valid @RequestBody EngineerRequestDto request
    ) {
        EngineerCreateResponse response = engineerService.registerAdminCreatedEngineer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.success(response));
    }

    @Operation(summary = "관리자 기술자 수정", description = "관리자가 기술자 프로필을 수정합니다.")
    @PatchMapping("/{engineerId}")
    public ResponseEntity<?> updateEngineerByAdmin(
            @PathVariable Long engineerId,
            @Valid @RequestBody EngineerRequestDto request
    ) {
        engineerService.updateEngineerByAdmin(engineerId, request);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "관리자 기술자 삭제", description = "관리자가 기술자 프로필을 삭제합니다.")
    @DeleteMapping("/{engineerId}")
    public ResponseEntity<?> deleteEngineerByAdmin(@PathVariable Long engineerId) {
        engineerService.deleteEngineerByAdmin(engineerId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
