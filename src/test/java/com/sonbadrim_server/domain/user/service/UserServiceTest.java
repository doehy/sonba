package com.sonbadrim_server.domain.user.service;

import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.dto.request.SignupRequestDto;
import com.sonbadrim_server.domain.user.entity.Gender;
import com.sonbadrim_server.domain.user.entity.RefreshToken;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.RefreshTokenRepository;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-hs512-signing-must-be-at-least-sixty-four-bytes-long-1234567890"
})
@Transactional
@Sql("/sql/engineer-test-data.sql")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("카카오 로그인 처리")
    class ProcessKakaoLogin {

        @Test
        @DisplayName("신규 카카오 사용자는 GUEST 사용자로 생성한다")
        void processKakaoLogin_createGuestUser() {
            // given
            Map<String, Object> kakaoUserInfo = Map.of("id", 900000000001L);

            // when
            Map<String, Object> result = userService.processKakaoLogin(kakaoUserInfo, "kakao-access-token");

            // then
            User savedUser = (User) result.get("user");
            assertThat(result.get("isNewUser")).isEqualTo(true);
            assertThat(savedUser.getUserId()).isNotNull();
            assertThat(savedUser.getKakaoId()).isEqualTo(900000000001L);
            assertThat(savedUser.getNickname()).isEqualTo("guest");
            assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
            assertThat(savedUser.getStatus()).isEqualTo(UserStatus.GUEST);
            assertThat(savedUser.getKakaoAccessToken()).isEqualTo("kakao-access-token");
        }

        @Test
        @DisplayName("기존 ACTIVE 사용자는 카카오 액세스 토큰만 갱신하고 회원가입이 필요하지 않다")
        void processKakaoLogin_updateExistingActiveUser() {
            // given
            User user = saveUser(900000000002L, UserStatus.ACTIVE, "old-token");
            Map<String, Object> kakaoUserInfo = Map.of("id", user.getKakaoId());

            // when
            Map<String, Object> result = userService.processKakaoLogin(kakaoUserInfo, "new-token");

            // then
            User resultUser = (User) result.get("user");
            assertThat(result.get("isNewUser")).isEqualTo(false);
            assertThat(resultUser.getUserId()).isEqualTo(user.getUserId());
            assertThat(resultUser.getKakaoAccessToken()).isEqualTo("new-token");
            assertThat(userRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("탈퇴한 사용자는 카카오 재로그인을 할 수 없다")
        void processKakaoLogin_rejectWithdrawnUser() {
            // given
            User user = saveUser(900000000003L, UserStatus.WITHDRAWN, "old-token");
            Map<String, Object> kakaoUserInfo = Map.of("id", user.getKakaoId());

            // when & then
            assertThatThrownBy(() -> userService.processKakaoLogin(kakaoUserInfo, "new-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("JWT와 Refresh Token")
    class Tokens {

        @Test
        @DisplayName("토큰 쌍 생성 시 refresh token 원문이 아닌 해시를 저장한다")
        void generateTokenPair_saveHashedRefreshToken() {
            // given
            User user = saveUser(900000000004L, UserStatus.ACTIVE, "kakao-token");

            // when
            Map<String, String> tokens = userService.generateTokenPair(user);

            // then
            assertThat(tokens.get("accessToken")).isNotBlank();
            assertThat(tokens.get("refreshToken")).isNotBlank();

            List<RefreshToken> refreshTokens = refreshTokenRepository.findAll();
            assertThat(refreshTokens).hasSize(1);
            assertThat(refreshTokens.getFirst().getUser().getUserId()).isEqualTo(user.getUserId());
            assertThat(refreshTokens.getFirst().getToken()).isNotEqualTo(tokens.get("refreshToken"));
            assertThat(refreshTokens.getFirst().getToken()).hasSize(64);
            assertThat(refreshTokens.getFirst().isRevoked()).isFalse();
        }

        @Test
        @DisplayName("refresh token 재발급 시 기존 토큰을 폐기하고 새 토큰을 저장한다")
        void reissueTokenPair_rotateRefreshToken() {
            // given
            User user = saveUser(900000000005L, UserStatus.ACTIVE, "kakao-token");
            Map<String, String> firstTokens = userService.generateTokenPair(user);

            // when
            Map<String, String> reissuedTokens = userService.reissueTokenPair(firstTokens.get("refreshToken"));

            // then
            assertThat(reissuedTokens.get("accessToken")).isNotBlank();
            assertThat(reissuedTokens.get("refreshToken")).isNotBlank();
            assertThat(reissuedTokens.get("refreshToken")).isNotEqualTo(firstTokens.get("refreshToken"));

            List<RefreshToken> refreshTokens = refreshTokenRepository.findAll();
            assertThat(refreshTokens).hasSize(2);
            assertThat(refreshTokens).anySatisfy(token -> assertThat(token.isRevoked()).isTrue());
            assertThat(refreshTokens).anySatisfy(token -> assertThat(token.isRevoked()).isFalse());
        }

        @Test
        @DisplayName("이미 폐기된 refresh token으로는 재발급할 수 없다")
        void reissueTokenPair_rejectRevokedRefreshToken() {
            // given
            User user = saveUser(900000000006L, UserStatus.ACTIVE, "kakao-token");
            Map<String, String> firstTokens = userService.generateTokenPair(user);
            userService.reissueTokenPair(firstTokens.get("refreshToken"));

            // when & then
            assertThatThrownBy(() -> userService.reissueTokenPair(firstTokens.get("refreshToken")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("access token에는 access 타입과 사용자 권한이 포함된다")
        void generateAccessToken_containsTypeAndRole() {
            // given
            User user = saveUser(900000000007L, UserStatus.ACTIVE, "kakao-token");

            // when
            String accessToken = userService.generateAccessToken(user);

            // then
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            var claims = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();

            assertThat(claims.getSubject()).isEqualTo(user.getUserId().toString());
            assertThat(claims.get("type")).isEqualTo("access");
            assertThat(claims.get("role")).isEqualTo(UserRole.USER.name());
        }
    }

    @Nested
    @DisplayName("회원가입과 로그아웃")
    class SignupAndLogout {

        @Test
        @DisplayName("GUEST 사용자는 추가 정보를 입력해 회원가입을 완료한다")
        void signup_completeGuestUser() {
            // given
            User user = saveUser(900000000008L, UserStatus.GUEST, "kakao-token");
            Region region = regionRepository.findAll().getFirst();
            setAuthenticatedUser(user);

            SignupRequestDto request = new SignupRequestDto();
            ReflectionTestUtils.setField(request, "regionId", region.getRegionId());
            ReflectionTestUtils.setField(request, "nickname", "손바드림");
            ReflectionTestUtils.setField(request, "birth", LocalDate.of(1995, 1, 1));
            ReflectionTestUtils.setField(request, "gender", Gender.MALE);

            // when
            userService.signup(request);

            // then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getRegion().getRegionId()).isEqualTo(region.getRegionId());
            assertThat(user.getNickname()).isEqualTo("손바드림");
            assertThat(user.getBirth()).isEqualTo(LocalDate.of(1995, 1, 1));
            assertThat(user.getGender()).isEqualTo(Gender.MALE);
        }

        @Test
        @DisplayName("ACTIVE 사용자는 회원가입을 다시 완료할 수 없다")
        void signup_rejectAlreadyActiveUser() {
            // given
            User user = saveUser(900000000009L, UserStatus.ACTIVE, "kakao-token");
            setAuthenticatedUser(user);

            SignupRequestDto request = new SignupRequestDto();

            // when & then
            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SIGNUP_FORBIDDEN);
        }

        @Test
        @DisplayName("로그아웃 시 활성 refresh token을 폐기하고 카카오 액세스 토큰을 제거한다")
        void logoutUser_revokeRefreshTokenAndClearKakaoToken() {
            // given
            User user = saveUser(900000000010L, UserStatus.ACTIVE, "kakao-token");
            userService.generateTokenPair(user);
            setAuthenticatedUser(user);

            // when
            userService.logoutUser();

            // then
            assertThat(user.getKakaoAccessToken()).isNull();
            entityManager.flush();
            entityManager.clear();
            assertThat(refreshTokenRepository.findAll())
                    .singleElement()
                    .satisfies(refreshToken -> assertThat(refreshToken.isRevoked()).isTrue());
        }
    }

    private User saveUser(Long kakaoId, UserStatus status, String kakaoAccessToken) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .nickname("테스트유저")
                .role(UserRole.USER)
                .status(status)
                .kakaoAccessToken(kakaoAccessToken)
                .build();
        return userRepository.save(user);
    }

    private void setAuthenticatedUser(User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getUserId().toString(),
                        null,
                        List.of()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
