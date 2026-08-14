package com.sonbadream_server.domain.user.controller;

import com.sonbadream_server.domain.user.dto.request.AdminVerificationRequestDto;
import com.sonbadream_server.domain.user.dto.response.AdminVerificationResponseDto;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 재인증 API", description = "관리자 메뉴 진입을 위한 추가 인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminVerificationController {

    private final UserService userService;

    @Operation(summary = "관리자 재인증", description = "관리자 전용 비밀번호를 검증하고 짧은 관리자 인증 토큰을 발급합니다.")
    @PostMapping("/verify")
    public ResponseEntity<?> verifyAdmin(@Valid @RequestBody AdminVerificationRequestDto request) {
        AdminVerificationResponseDto response = userService.verifyAdminPassword(request);
        return ResponseEntity.ok(CommonResponse.success(response));
    }
}
