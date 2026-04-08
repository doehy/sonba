package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerCreateRequest;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<?> registerEngineer(@Valid @RequestBody EngineerCreateRequest request) {
        EngineerCreateResponse response = engineerService.registerEngineer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.success(response));
    }

    @Operation(summary = "기술자 수정", description = "현재 로그인 사용자의 기술자 정보를 수정합니다.")
    @PatchMapping("/{engineerId}")
    public ResponseEntity<?> updateEngineer(@PathVariable Long engineerId,
                                            @Valid @RequestBody EngineerCreateRequest request) {
        engineerService.updateEngineer(engineerId, request);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
