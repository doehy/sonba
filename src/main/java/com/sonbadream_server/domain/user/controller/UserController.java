package com.sonbadream_server.domain.user.controller;

import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.dto.response.EngineerRegistrationStatusResponseDto;
import com.sonbadream_server.domain.user.dto.request.UpdateUserProfileRequestDto;
import com.sonbadream_server.domain.user.dto.response.UserProfileResponseDto;
import com.sonbadream_server.domain.user.service.KakaoService;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


/**
 * 사용자 관리 컨트롤러
 */
@Tag(name = "사용자", description = "사용자 정보 및 설정 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Slf4j
public class UserController {

    private final UserService userService;
    private final KakaoService kakaoService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보를 조회합니다.")
    public ResponseEntity<?> getCurrentUser() {
        User currentUser = userService.getCurrentUser();

        Map<String, Object> userInfo = Map.of(
                "userId", currentUser.getUserId(),
                "kakaoId", currentUser.getKakaoId(),
                "nickname", currentUser.getNickname(),
                "status", currentUser.getStatus(),
                "role", currentUser.getRole()
        );

        return ResponseEntity.ok(CommonResponse.success(userInfo));
    }

    @GetMapping("/profile")
    @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    public ResponseEntity<?> getMyProfile() {
        UserProfileResponseDto response = userService.getMyProfile();
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @GetMapping("/engineer-registration")
    @Operation(summary = "기술자 등록 여부 조회", description = "현재 로그인한 사용자의 기술자 등록 여부만 조회합니다.")
    public ResponseEntity<?> getEngineerRegistrationStatus() {
        EngineerRegistrationStatusResponseDto response = userService.getEngineerRegistrationStatus();
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @PatchMapping("/profile")
    @Operation(summary = "내 프로필 수정", description = "현재 로그인한 사용자의 프로필 정보를 수정합니다.")
    public ResponseEntity<?> updateMyProfile(@Valid @RequestBody UpdateUserProfileRequestDto requestDto) {
        userService.updateMyProfile(requestDto);
        return ResponseEntity.ok(CommonResponse.success("프로필이 성공적으로 업데이트되었습니다."));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자를 탈퇴 처리합니다.")
    public ResponseEntity<?> withdrawUser() {
        try {
            User currentUser = userService.getCurrentUser();

            if (currentUser.getKakaoAccessToken() != null) {
                kakaoService.unlink(currentUser.getKakaoAccessToken());
            }

            userService.withdrawUser();
            return ResponseEntity.ok(CommonResponse.success(null));
        } catch (Exception e) {
            userService.withdrawUser();
            return ResponseEntity.ok(CommonResponse.success(null));
        }
    }
}
