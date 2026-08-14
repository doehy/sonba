package com.sonbadream_server.global.filter;

import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-for-tests-32bytes";

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더가 없어도 accessToken cookie로 사용자를 인증한다")
    void authenticateWithAccessTokenCookie() throws Exception {
        // given
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(userRepository, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);

        User user = User.builder()
                .userId(1L)
                .kakaoId(900000000001L)
                .nickname("테스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.setCookies(new Cookie("accessToken", createAccessToken(1L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
    }

    private String createAccessToken(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 3_600_000);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("type", "access")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key)
                .compact();
    }
}
