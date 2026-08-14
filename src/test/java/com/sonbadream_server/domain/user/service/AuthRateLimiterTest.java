package com.sonbadream_server.domain.user.service;

import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    @Test
    @DisplayName("허용 횟수를 초과하면 TOO_MANY_REQUESTS를 발생시킨다")
    void check_rejectOverLimit() {
        // given
        AuthRateLimiter authRateLimiter = new AuthRateLimiter(true, 2, 60, 100);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        // when
        authRateLimiter.check(request, "refresh");
        authRateLimiter.check(request, "refresh");

        // then
        assertThatThrownBy(() -> authRateLimiter.check(request, "refresh"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("X-Forwarded-For의 첫 번째 IP를 클라이언트 IP로 사용한다")
    void check_useFirstForwardedForIp() {
        // given
        AuthRateLimiter authRateLimiter = new AuthRateLimiter(true, 1, 60, 100);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        // when
        authRateLimiter.check(request, "kakao-callback");

        // then
        assertThatThrownBy(() -> authRateLimiter.check(request, "kakao-callback"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("비활성화되어 있으면 요청 횟수를 제한하지 않는다")
    void check_doNothingWhenDisabled() {
        // given
        AuthRateLimiter authRateLimiter = new AuthRateLimiter(false, 1, 60, 100);
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        authRateLimiter.check(request, "refresh");
        authRateLimiter.check(request, "refresh");
        authRateLimiter.check(request, "refresh");
    }
}
