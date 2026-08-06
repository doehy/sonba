package com.sonbadrim_server.domain.user.controller;

import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.AuthRateLimiter;
import com.sonbadrim_server.domain.user.service.KakaoService;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AuthController.class)
@TestPropertySource(properties = {
        "kakao.client.id=test-client-id",
        "kakao.redirect.uri=http://localhost:8080/auth/kakao/callback",
        "app.frontend-url=http://localhost:5173",
        "jwt.access-expiration=3600000",
        "jwt.refresh-expiration=1209600000",
        "app.cookie-secure=false",
        "app.cookie-same-site=Lax"
})
class AuthControllerTest {

    @MockitoBean
    private KakaoService kakaoService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthRateLimiter authRateLimiter;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("카카오 로그인")
    class KakaoLogin {

        @Test
        @DisplayName("카카오 로그인 시작 시 카카오 인가 페이지로 리다이렉트한다")
        void redirectToKakaoLogin_success() throws Exception {
            // when & then
            mockMvc.perform(get("/auth/kakao/login"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", containsString("https://kauth.kakao.com/oauth/authorize")))
                    .andExpect(header().string("Location", containsString("client_id=test-client-id")))
                    .andExpect(header().string("Location", containsString("response_type=code")))
                    .andExpect(header().string("Location", containsString("state=")))
                    .andExpect(header().string("Set-Cookie", containsString("kakaoOAuthState=")))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                    .andExpect(header().string(
                            "Location",
                            containsString("redirect_uri=" + URLEncoder.encode(
                                    "http://localhost:8080/auth/kakao/callback",
                                    StandardCharsets.UTF_8
                            ))
                    ));
        }

        @Test
        @DisplayName("신규 카카오 사용자는 토큰 cookie를 받고 회원가입 화면으로 이동한다")
        void kakaoLogin_newUserRedirectToSignup() throws Exception {
            // given
            User user = createUser(900000000001L, UserStatus.GUEST, "kakao-token");
            Map<String, Object> kakaoUserInfo = Map.of("id", 900000000001L);

            given(kakaoService.getAccessToken("code"))
                    .willReturn("kakao-token");
            given(kakaoService.getUserInfo("kakao-token"))
                    .willReturn(kakaoUserInfo);
            given(userService.processKakaoLogin(kakaoUserInfo, "kakao-token"))
                    .willReturn(Map.of("user", user, "isNewUser", true));
            given(userService.generateTokenPair(user))
                    .willReturn(Map.of("accessToken", "access-token", "refreshToken", "refresh-token"));

            // when & then
            mockMvc.perform(get("/auth/kakao/callback")
                            .param("code", "code")
                            .param("state", "oauth-state")
                            .cookie(new Cookie("kakaoOAuthState", "oauth-state")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "http://localhost:5173/signup"))
                    .andExpect(cookie().value("accessToken", "access-token"))
                    .andExpect(cookie().httpOnly("accessToken", true))
                    .andExpect(cookie().value("refreshToken", "refresh-token"))
                    .andExpect(cookie().httpOnly("refreshToken", true))
                    .andExpect(cookie().value("kakaoOAuthState", ""))
                    .andExpect(cookie().maxAge("kakaoOAuthState", 0));
        }

        @Test
        @DisplayName("기존 카카오 사용자는 토큰 cookie를 받고 메인 화면으로 이동한다")
        void kakaoLogin_existingUserRedirectToMain() throws Exception {
            // given
            User user = createUser(900000000002L, UserStatus.ACTIVE, "kakao-token");
            Map<String, Object> kakaoUserInfo = Map.of("id", 900000000002L);

            given(kakaoService.getAccessToken("code"))
                    .willReturn("kakao-token");
            given(kakaoService.getUserInfo("kakao-token"))
                    .willReturn(kakaoUserInfo);
            given(userService.processKakaoLogin(kakaoUserInfo, "kakao-token"))
                    .willReturn(Map.of("user", user, "isNewUser", false));
            given(userService.generateTokenPair(user))
                    .willReturn(Map.of("accessToken", "access-token", "refreshToken", "refresh-token"));

            // when & then
            mockMvc.perform(get("/auth/kakao/callback")
                            .param("code", "code")
                            .param("state", "oauth-state")
                            .cookie(new Cookie("kakaoOAuthState", "oauth-state")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "http://localhost:5173/"))
                    .andExpect(cookie().value("accessToken", "access-token"))
                    .andExpect(cookie().httpOnly("accessToken", true))
                    .andExpect(cookie().value("refreshToken", "refresh-token"))
                    .andExpect(cookie().httpOnly("refreshToken", true))
                    .andExpect(cookie().value("kakaoOAuthState", ""))
                    .andExpect(cookie().maxAge("kakaoOAuthState", 0));
        }

        @Test
        @DisplayName("카카오 콜백 state가 쿠키와 다르면 로그인을 거부한다")
        void kakaoLogin_invalidState() throws Exception {
            // when & then
            mockMvc.perform(get("/auth/kakao/callback")
                            .param("code", "code")
                            .param("state", "attacker-state")
                            .cookie(new Cookie("kakaoOAuthState", "victim-state")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));

            verify(kakaoService, never()).getAccessToken(any());
        }

        @Test
        @DisplayName("카카오 콜백 state 쿠키가 없으면 로그인을 거부한다")
        void kakaoLogin_missingStateCookie() throws Exception {
            // when & then
            mockMvc.perform(get("/auth/kakao/callback")
                            .param("code", "code")
                            .param("state", "oauth-state"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));

            verify(kakaoService, never()).getAccessToken(any());
        }

        @Test
        @DisplayName("카카오 콜백에 code 파라미터가 없으면 400을 반환한다")
        void kakaoLogin_missingCode() throws Exception {
            // when & then
            mockMvc.perform(get("/auth/kakao/callback"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.data.message").value("필수 파라미터 'code'이(가) 누락되었습니다."));

            verify(kakaoService, never()).getAccessToken(any());
        }

        @Test
        @DisplayName("카카오 콜백 처리 중 일반 예외가 발생하면 카카오 로그인 실패로 응답한다")
        void kakaoLogin_generalException() throws Exception {
            // given
            given(kakaoService.getAccessToken(""))
                    .willThrow(new RuntimeException("invalid code"));

            // when & then
            mockMvc.perform(get("/auth/kakao/callback")
                            .param("code", "")
                            .param("state", "oauth-state")
                            .cookie(new Cookie("kakaoOAuthState", "oauth-state")))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("KAKAO_LOGIN_FAILED"));
        }

        @Test
        @DisplayName("카카오 콜백 요청이 너무 많으면 로그인을 시도하지 않고 거부한다")
        void kakaoLogin_rateLimited() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.TOO_MANY_REQUESTS))
                    .given(authRateLimiter).check(any(), any());

            // when & then
            mockMvc.perform(get("/auth/kakao/callback")
                            .param("code", "code"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("TOO_MANY_REQUESTS"));

            verify(kakaoService, never()).getAccessToken(any());
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Refresh {

        @Test
        @DisplayName("허용된 Origin과 refresh cookie로 토큰 cookie를 재발급한다")
        void refresh_success() throws Exception {
            // given
            given(userService.reissueTokenPair("old-refresh-token"))
                    .willReturn(Map.of("accessToken", "new-access-token", "refreshToken", "new-refresh-token"));

            // when & then
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new Cookie("refreshToken", "old-refresh-token"))
                            .header("Origin", "http://localhost:5173"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(cookie().value("accessToken", "new-access-token"))
                    .andExpect(cookie().httpOnly("accessToken", true))
                    .andExpect(cookie().value("refreshToken", "new-refresh-token"))
                    .andExpect(cookie().httpOnly("refreshToken", true));
        }

        @Test
        @DisplayName("허용되지 않은 Origin이면 토큰 재발급을 거부한다")
        void refresh_forbiddenOrigin() throws Exception {
            // when & then
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new Cookie("refreshToken", "old-refresh-token"))
                            .header("Origin", "http://evil.example.com"))
                    .andExpect(status().isForbidden());

            verify(userService, never()).reissueTokenPair(any());
        }

        @Test
        @DisplayName("Origin이 없어도 허용된 Referer이면 access token을 재발급한다")
        void refresh_successWithAllowedReferer() throws Exception {
            // given
            given(userService.reissueTokenPair("old-refresh-token"))
                    .willReturn(Map.of("accessToken", "new-access-token", "refreshToken", "new-refresh-token"));

            // when & then
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new Cookie("refreshToken", "old-refresh-token"))
                            .header("Referer", "http://localhost:5173/signup?step=profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(cookie().value("accessToken", "new-access-token"))
                    .andExpect(cookie().value("refreshToken", "new-refresh-token"))
                    .andExpect(cookie().httpOnly("refreshToken", true));
        }

        @Test
        @DisplayName("Origin이 없고 허용되지 않은 Referer이면 토큰 재발급을 거부한다")
        void refresh_forbiddenReferer() throws Exception {
            // when & then
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new Cookie("refreshToken", "old-refresh-token"))
                            .header("Referer", "http://evil.example.com/signup?step=profile"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));

            verify(userService, never()).reissueTokenPair(any());
        }

        @Test
        @DisplayName("refresh cookie가 없으면 토큰 재발급을 거부한다")
        void refresh_missingCookie() throws Exception {
            // given
            given(userService.reissueTokenPair(isNull()))
                    .willThrow(new CustomException(ErrorCode.UNAUTHORIZED));

            // when & then
            mockMvc.perform(post("/auth/refresh")
                            .header("Origin", "http://localhost:5173"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));

            verify(userService).reissueTokenPair(null);
        }

        @Test
        @DisplayName("토큰 재발급 요청이 너무 많으면 재발급을 시도하지 않고 거부한다")
        void refresh_rateLimited() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.TOO_MANY_REQUESTS))
                    .given(authRateLimiter).check(any(), any());

            // when & then
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new Cookie("refreshToken", "old-refresh-token"))
                            .header("Origin", "http://localhost:5173"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("TOO_MANY_REQUESTS"));

            verify(userService, never()).reissueTokenPair(any());
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃 시 카카오 연결을 끊고 refresh cookie를 삭제한다")
        void logout_success() throws Exception {
            // given
            User user = createUser(900000000003L, UserStatus.ACTIVE, "kakao-token");
            given(userService.getCurrentUser()).willReturn(user);

            // when & then
            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(cookie().value("accessToken", ""))
                    .andExpect(cookie().maxAge("accessToken", 0))
                    .andExpect(cookie().value("refreshToken", ""))
                    .andExpect(cookie().maxAge("refreshToken", 0));

            verify(kakaoService).unlink("kakao-token");
            verify(userService).logoutUser();
        }

        @Test
        @DisplayName("로그아웃 처리 중 예외가 발생해도 클라이언트 cookie는 삭제한다")
        void logout_clearCookieEvenIfLogoutFails() throws Exception {
            // given
            given(userService.getCurrentUser())
                    .willThrow(new RuntimeException("logout failed"));

            // when & then
            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(cookie().value("accessToken", ""))
                    .andExpect(cookie().maxAge("accessToken", 0))
                    .andExpect(cookie().value("refreshToken", ""))
                    .andExpect(cookie().maxAge("refreshToken", 0));
        }
    }

    private User createUser(Long kakaoId, UserStatus status, String kakaoAccessToken) {
        return User.builder()
                .kakaoId(kakaoId)
                .nickname("테스트유저")
                .role(UserRole.USER)
                .status(status)
                .kakaoAccessToken(kakaoAccessToken)
                .build();
    }
}
