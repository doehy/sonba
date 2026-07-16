package com.sonbadrim_server.domain.user.controller;

//import org.springframework.data.redis.core.RedisTemplate;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.dto.request.SignupRequestDto;
import com.sonbadrim_server.domain.user.dto.response.AuthTokenResponse;
import com.sonbadrim_server.domain.user.service.AuthRateLimiter;
import com.sonbadrim_server.domain.user.service.KakaoService;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.common.CommonResponse;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {

    private final KakaoService kakaoService;
    private final UserService userService;
    private final AuthRateLimiter authRateLimiter;

    @Value("${kakao.client.id}")
    private String clientId;

    @Value("${kakao.redirect.uri}")
    private String redirectUri;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie-same-site:Lax}")
    private String cookieSameSite;

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
    public void kakaoLogin(
            @RequestParam String code,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        authRateLimiter.check(request, "kakao-callback");
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
            addRefreshTokenCookie(response, tokens.get("refreshToken"));

            String redirectPath = requiresSignup ? "/signup" : "/";
            String redirectTarget = frontendUrl + redirectPath
                    + "#accessToken=" + URLEncoder.encode(tokens.get("accessToken"), StandardCharsets.UTF_8);

            response.sendRedirect(redirectTarget);
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
    public ResponseEntity<?> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authRateLimiter.check(request, "refresh");
        validateRefreshOrigin(request);
        Map<String, String> tokens = userService.reissueTokenPair(refreshToken);
        addRefreshTokenCookie(response, tokens.get("refreshToken"));
        AuthTokenResponse body = new AuthTokenResponse(tokens.get("accessToken"), null);
        return ResponseEntity.ok(CommonResponse.success(body));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 사용자의 세션을 종료하고 refresh 토큰을 폐기합니다.")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        try {
            User currentUser = userService.getCurrentUser();

            // 저장된 카카오 액세스 토큰으로 카카오 연결끊기 호출 (완전한 로그아웃)
            if (currentUser.getKakaoAccessToken() != null) {
                kakaoService.unlink(currentUser.getKakaoAccessToken());
            }

            // 사용자의 카카오 토큰 제거
            userService.logoutUser();
            clearRefreshTokenCookie(response);

            return ResponseEntity.ok(CommonResponse.success(null));
        } catch (Exception e) {
            // 로그아웃 실패해도 200 반환 (클라이언트 토큰은 삭제되어야 함)
            log.warn("로그아웃 처리 중 오류: {}", e.getMessage());
            clearRefreshTokenCookie(response);
            return ResponseEntity.ok(CommonResponse.success(null));
        }
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        StringBuilder cookie = new StringBuilder();
        cookie.append("refreshToken=").append(refreshToken).append("; ");
        cookie.append("Max-Age=").append(Duration.ofMillis(refreshTokenExpiration).toSeconds()).append("; ");
        cookie.append("Path=/; HttpOnly; SameSite=").append(cookieSameSite).append(";");
        if (cookieSecure) {
            cookie.append(" Secure;");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        StringBuilder cookie = new StringBuilder("refreshToken=; Max-Age=0; Path=/; HttpOnly; SameSite=")
                .append(cookieSameSite)
                .append(";");
        if (cookieSecure) {
            cookie.append(" Secure;");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void validateRefreshOrigin(HttpServletRequest request) {
        String expectedOrigin = normalizeOrigin(frontendUrl);
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            if (!expectedOrigin.equals(normalizeOrigin(origin))) {
                throw new CustomException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            String refererOrigin = extractOrigin(referer);
            if (expectedOrigin.equals(normalizeOrigin(refererOrigin))) {
                return;
            }
        }

        throw new CustomException(ErrorCode.FORBIDDEN);
    }

    private String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    private String extractOrigin(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null || host == null) {
                return "";
            }

            if (port == -1) {
                return scheme + "://" + host;
            }

            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
