package com.sonbadrim_server.domain.user.controller;

//import org.springframework.data.redis.core.RedisTemplate;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.dto.request.RefreshTokenRequestDto;
import com.sonbadrim_server.domain.user.dto.request.SignupRequestDto;
import com.sonbadrim_server.domain.user.dto.response.AuthTokenResponse;
import com.sonbadrim_server.domain.user.dto.response.LoginResponse;
import com.sonbadrim_server.domain.user.service.KakaoService;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.common.CommonResponse;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {

    private final KakaoService kakaoService;
    private final UserService userService;

    @Value("${kakao.client.id}")
    private String clientId;

    @Value("${kakao.redirect.uri}")
    private String redirectUri;

    /**
     * 카카오 로그인 시작
     * 사용자를 카카오 인가 페이지로 리다이렉트
     */
    @Operation(summary = "카카오 로그인 시작", description = "카카오 인가 페이지로 리다이렉트합니다.")
    @GetMapping("/kakao/login")
    public void redirectToKakaoLogin(HttpServletResponse response) throws IOException {
        String kakaoAuthUrl = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code";

        response.sendRedirect(kakaoAuthUrl);
    }


    @Operation(
            summary = "카카오 로그인 콜백",
            description = "Access/Refresh 토큰을 발급하고, 회원가입 필요 여부(requiresSignup)를 반환합니다."
    )
    @GetMapping("/kakao/callback")
    public ResponseEntity<?> kakaoLogin(@RequestParam String code) {
        try {
            // 1. 인증 코드로 카카오 액세스 토큰 받기
            String kakaoAccessToken = kakaoService.getAccessToken(code);

            // 2. 카카오 액세스 토큰으로 사용자 정보 받기
            Map<String, Object> kakaoUserInfo = kakaoService.getUserInfo(kakaoAccessToken);

            // 3. 사용자 정보 저장 또는 업데이트
            Map<String, Object> loginResult = userService.processKakaoLogin(kakaoUserInfo, kakaoAccessToken);
            User user = (User) loginResult.get("user");
            boolean requiresSignup = (boolean) loginResult.get("isNewUser");

            // 4. JWT 토큰 생성 (Access + Refresh)
            Map<String, String> tokens = userService.generateTokenPair(user);

            // 5. refreshToken Redis에 저장 (VITE_API_BASE_URL local 시, 주석)
            // redisTemplate.opsForValue()
            //         .set(user.getUserId().toString(), tokens.get("refreshToken"), Duration.ofMillis(REFRESH_TOKEN_EXPIRE_TIME));

            // 6. 응답 데이터
            LoginResponse response = new LoginResponse(
                    new AuthTokenResponse(tokens.get("accessToken"), tokens.get("refreshToken")),
                    requiresSignup
            );

            return ResponseEntity.ok(CommonResponse.success(response));
        } catch (CustomException e) {
            // CustomException은 그대로 재던지기 (탈퇴한 사용자 403 에러 포함)
            throw e;
        } catch (Exception e) {
            // 기타 예외는 502 에러로 처리
            throw new CustomException(ErrorCode.KAKAO_LOGIN_FAILED);
        }
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입 완료", description = "GUEST 사용자의 추가 정보를 입력받아 회원가입을 완료합니다.")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequestDto signupRequest) {
        userService.signup(signupRequest);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "Refresh 토큰으로 Access/Refresh 토큰을 재발급합니다(rotate 정책).")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequestDto requestDto) {
        Map<String, String> tokens = userService.reissueTokenPair(requestDto.getRefreshToken());
        AuthTokenResponse response = new AuthTokenResponse(tokens.get("accessToken"), tokens.get("refreshToken"));
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 사용자의 세션을 종료하고 refresh 토큰을 폐기합니다.")
    public ResponseEntity<?> logout() {
        try {
            User currentUser = userService.getCurrentUser();

            // 저장된 카카오 액세스 토큰으로 카카오 연결끊기 호출 (완전한 로그아웃)
            if (currentUser.getKakaoAccessToken() != null) {
                kakaoService.unlink(currentUser.getKakaoAccessToken());
            }

            // 사용자의 카카오 토큰 제거
            userService.logoutUser();

            return ResponseEntity.ok(CommonResponse.success(null));
        } catch (Exception e) {
            // 로그아웃 실패해도 200 반환 (클라이언트 토큰은 삭제되어야 함)
            log.warn("로그아웃 처리 중 오류: {}", e.getMessage());
            return ResponseEntity.ok(CommonResponse.success(null));
        }
    }
}
