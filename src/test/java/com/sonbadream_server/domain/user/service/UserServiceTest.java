package com.sonbadream_server.domain.user.service;

import com.sonbadream_server.domain.region.entity.Region;
import com.sonbadream_server.domain.region.repository.RegionRepository;
import com.sonbadream_server.domain.user.dto.request.AdminVerificationRequestDto;
import com.sonbadream_server.domain.user.dto.request.SignupRequestDto;
import com.sonbadream_server.domain.user.entity.Gender;
import com.sonbadream_server.domain.user.entity.RefreshToken;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.RefreshTokenRepository;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private PasswordEncoder passwordEncoder;

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

        @Test
        @DisplayName("정지된 사용자는 카카오 재로그인을 할 수 없다")
        void processKakaoLogin_rejectSuspendedUser() {
            // given
            User user = saveUser(900000000014L, UserStatus.SUSPENDED, "old-token");
            Map<String, Object> kakaoUserInfo = Map.of("id", user.getKakaoId());

            // when & then
            assertThatThrownBy(() -> userService.processKakaoLogin(kakaoUserInfo, "new-token"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_SUSPENDED);
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
        @DisplayName("정지된 사용자는 refresh token으로 토큰을 재발급할 수 없다")
        void reissueTokenPair_rejectSuspendedUser() {
            // given
            User user = saveUser(900000000015L, UserStatus.ACTIVE, "kakao-token");
            Map<String, String> tokens = userService.generateTokenPair(user);
            user.suspend();
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThatThrownBy(() -> userService.reissueTokenPair(tokens.get("refreshToken")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_SUSPENDED);
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

    @Nested
    @DisplayName("관리자 유저 상태 관리")
    class AdminUserManagement {

        @Test
        @DisplayName("관리자는 유저 목록을 조회할 수 있다")
        void getAdminUsers_success() {
            // given
            User admin = saveUser(900000000026L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000027L, UserStatus.ACTIVE, "target-token");
            setAuthenticatedUser(admin);

            // when
            var response = userService.getAdminUsers(null, PageRequest.of(0, 100));

            // then
            assertThat(response.getContent())
                    .anySatisfy(user -> {
                        assertThat(user.getUserId()).isEqualTo(target.getUserId());
                        assertThat(user.getNickname()).isEqualTo(target.getNickname());
                        assertThat(user.getRole()).isEqualTo(UserRole.USER);
                        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                        assertThat(user.isEngineerRegistered()).isFalse();
                    });
        }

        @Test
        @DisplayName("관리자 비밀번호가 일치하면 관리자 인증 토큰을 발급한다")
        void verifyAdminPassword_success() {
            // given
            User admin = saveUser(900000000035L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            setAuthenticatedUser(admin);
            ReflectionTestUtils.setField(
                    userService,
                    "adminVerificationPasswordHash",
                    passwordEncoder.encode("admin-password")
            );

            AdminVerificationRequestDto request = new AdminVerificationRequestDto();
            ReflectionTestUtils.setField(request, "password", "admin-password");

            // when
            var response = userService.verifyAdminPassword(request);

            // then
            assertThat(response.getAdminVerifiedToken()).isNotBlank();
        }

        @Test
        @DisplayName("관리자 비밀번호가 일치하지 않으면 관리자 인증 토큰을 발급하지 않는다")
        void verifyAdminPassword_wrongPassword() {
            // given
            User admin = saveUser(900000000036L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            setAuthenticatedUser(admin);
            ReflectionTestUtils.setField(
                    userService,
                    "adminVerificationPasswordHash",
                    passwordEncoder.encode("admin-password")
            );

            AdminVerificationRequestDto request = new AdminVerificationRequestDto();
            ReflectionTestUtils.setField(request, "password", "wrong-password");

            // when & then
            assertThatThrownBy(() -> userService.verifyAdminPassword(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ADMIN_VERIFICATION_FAILED);
        }

        @Test
        @DisplayName("관리자가 아니면 관리자 재인증을 할 수 없다")
        void verifyAdminPassword_forbidden() {
            // given
            User user = saveUser(900000000037L, UserStatus.ACTIVE, "user-token");
            setAuthenticatedUser(user);

            AdminVerificationRequestDto request = new AdminVerificationRequestDto();
            ReflectionTestUtils.setField(request, "password", "admin-password");

            // when & then
            assertThatThrownBy(() -> userService.verifyAdminPassword(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("관리자가 아니면 유저 목록을 조회할 수 없다")
        void getAdminUsers_forbidden() {
            // given
            User user = saveUser(900000000028L, UserStatus.ACTIVE, "user-token");
            setAuthenticatedUser(user);

            // when & then
            assertThatThrownBy(() -> userService.getAdminUsers(null, PageRequest.of(0, 20)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("관리자는 userId로 유저를 검색할 수 있다")
        void getAdminUsers_searchByUserId() {
            // given
            User admin = saveUser(900000000029L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000030L, UserStatus.ACTIVE, "target-token");
            saveUser(900000000031L, UserStatus.ACTIVE, "other-token");
            setAuthenticatedUser(admin);

            // when
            var response = userService.getAdminUsers(target.getUserId().toString(), PageRequest.of(0, 20));

            // then
            assertThat(response.getContent())
                    .extracting("userId")
                    .containsExactly(target.getUserId());
        }

        @Test
        @DisplayName("관리자는 닉네임으로 유저를 검색할 수 있다")
        void getAdminUsers_searchByNickname() {
            // given
            User admin = saveUser(900000000032L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000033L, UserStatus.ACTIVE, "target-token", UserRole.USER, "검색대상");
            saveUser(900000000034L, UserStatus.ACTIVE, "other-token", UserRole.USER, "다른유저");
            setAuthenticatedUser(admin);

            // when
            var response = userService.getAdminUsers("검색", PageRequest.of(0, 20));

            // then
            assertThat(response.getContent())
                    .extracting("nickname")
                    .contains(target.getNickname())
                    .doesNotContain("다른유저");
        }

        @Test
        @DisplayName("관리자는 ACTIVE 사용자를 일시 정지할 수 있다")
        void suspendUserByAdmin_success() {
            // given
            User admin = saveUser(900000000016L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000017L, UserStatus.ACTIVE, "target-token");
            userService.generateTokenPair(target);
            setAuthenticatedUser(admin);

            // when
            userService.suspendUserByAdmin(target.getUserId());

            // then
            assertThat(target.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            entityManager.flush();
            entityManager.clear();
            assertThat(refreshTokenRepository.findAll())
                    .allSatisfy(refreshToken -> assertThat(refreshToken.isRevoked()).isTrue());
        }

        @Test
        @DisplayName("관리자는 SUSPENDED 사용자의 정지를 해제할 수 있다")
        void activateUserByAdmin_success() {
            // given
            User admin = saveUser(900000000018L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000019L, UserStatus.SUSPENDED, "target-token");
            setAuthenticatedUser(admin);

            // when
            userService.activateUserByAdmin(target.getUserId());

            // then
            assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("관리자가 아니면 사용자를 일시 정지할 수 없다")
        void suspendUserByAdmin_forbidden() {
            // given
            User user = saveUser(900000000020L, UserStatus.ACTIVE, "user-token");
            User target = saveUser(900000000021L, UserStatus.ACTIVE, "target-token");
            setAuthenticatedUser(user);

            // when & then
            assertThatThrownBy(() -> userService.suspendUserByAdmin(target.getUserId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("탈퇴한 사용자는 일시 정지할 수 없다")
        void suspendUserByAdmin_rejectWithdrawnUser() {
            // given
            User admin = saveUser(900000000022L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000023L, UserStatus.WITHDRAWN, "target-token");
            setAuthenticatedUser(admin);

            // when & then
            assertThatThrownBy(() -> userService.suspendUserByAdmin(target.getUserId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_WITHDRAWN);
        }

        @Test
        @DisplayName("GUEST 사용자는 정지 해제로 ACTIVE 상태가 될 수 없다")
        void activateUserByAdmin_rejectGuestUser() {
            // given
            User admin = saveUser(900000000024L, UserStatus.ACTIVE, "admin-token", UserRole.ADMIN);
            User target = saveUser(900000000025L, UserStatus.GUEST, "target-token");
            setAuthenticatedUser(admin);

            // when & then
            assertThatThrownBy(() -> userService.activateUserByAdmin(target.getUserId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_INVALID_STATUS);
        }
    }

    private User saveUser(Long kakaoId, UserStatus status, String kakaoAccessToken) {
        return saveUser(kakaoId, status, kakaoAccessToken, UserRole.USER);
    }

    private User saveUser(Long kakaoId, UserStatus status, String kakaoAccessToken, UserRole role) {
        return saveUser(kakaoId, status, kakaoAccessToken, role, "테스트유저");
    }

    private User saveUser(Long kakaoId, UserStatus status, String kakaoAccessToken, UserRole role, String nickname) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(role)
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
