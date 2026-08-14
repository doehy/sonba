package com.sonbadream_server.domain.user.service;

import com.sonbadream_server.domain.region.entity.Region;
import com.sonbadream_server.domain.region.repository.RegionRepository;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.entity.RefreshToken;
import com.sonbadream_server.domain.user.dto.request.AdminVerificationRequestDto;
import com.sonbadream_server.domain.user.dto.request.SignupRequestDto;
import com.sonbadream_server.domain.user.dto.request.UpdateUserProfileRequestDto;
import com.sonbadream_server.domain.user.dto.response.UserListItemResponseDto;
import com.sonbadream_server.domain.user.dto.response.AdminVerificationResponseDto;
import com.sonbadream_server.domain.user.dto.response.EngineerRegistrationStatusResponseDto;
import com.sonbadream_server.domain.user.dto.response.UserProfileResponseDto;
import com.sonbadream_server.domain.user.repository.RefreshTokenRepository;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;

    @Value("${admin.verification.password-hash:}")
    private String adminVerificationPasswordHash;

    @Value("${admin.verification.token-expiration:1800000}")
    private long adminVerificationTokenExpiration;


    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public User getMemberById(Long memberId){
        return userRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 카카오 로그인 처리: 기존 사용자 조회 또는 신규 사용자 생성
     * @param kakaoUserInfo 카카오 사용자 정보
     * @param kakaoAccessToken 카카오 액세스 토큰
     * @return Map containing user and isNewUser flag
     */
    public Map<String, Object> processKakaoLogin(Map<String, Object> kakaoUserInfo, String kakaoAccessToken) {
        Long kakaoId = Long.valueOf(kakaoUserInfo.get("id").toString());

        // 기존 사용자 조회
        Optional<User> existingUser = userRepository.findByKakaoId(kakaoId);

        Map<String, Object> result = new HashMap<>();

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // 탈퇴한 사용자(WITHDRAWN)는 재로그인 금지
            if (UserStatus.WITHDRAWN.equals(user.getStatus())) {
                throw new CustomException(ErrorCode.USER_WITHDRAWN);
            }
            if (UserStatus.SUSPENDED.equals(user.getStatus())) {
                throw new CustomException(ErrorCode.USER_SUSPENDED);
            }

            // 카카오 액세스 토큰 업데이트
            user.updateKakaoAccessToken(kakaoAccessToken);
            userRepository.save(user);

            // 기존 사용자 - GUEST 상태면 회원가입 필요, ACTIVE면 회원가입 완료
            result.put("user", user);
            result.put("isNewUser", UserStatus.GUEST.equals(user.getStatus()));
        } else {
            // 신규 사용자 생성
            User newUser = User.builder()
                    .kakaoId(kakaoId)
                    .nickname("guest")
                    .role(UserRole.USER)
                    .status(UserStatus.GUEST)
                    .kakaoAccessToken(kakaoAccessToken)
                    .build();

            User savedUser = userRepository.save(newUser);
            result.put("user", savedUser);
            result.put("isNewUser", true);
        }

        return result;
    }

    /**
     * JWT Access Token 생성
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("type", "access")
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * JWT Refresh Token 생성
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);
        String tokenId = UUID.randomUUID().toString();

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("type", "refresh")
                .id(tokenId)
                .issuedAt(now)

                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * 토큰 쌍 생성 (Access + Refresh)
     */
    public Map<String, String> generateTokenPair(User user) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        rotateRefreshToken(user, refreshToken);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        return tokens;
    }

    public Map<String, String> reissueTokenPair(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            claims = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(refreshToken)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Object tokenType = claims.get("type");
        if (!"refresh".equals(tokenType)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        if (UserStatus.WITHDRAWN.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_SUSPENDED);
        }

        String refreshTokenHash = hashRefreshToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findValidToken(userId, refreshTokenHash, LocalDateTime.now())
                .orElseThrow(() -> {
                    logRefreshTokenReuseIfRevoked(userId, refreshTokenHash);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });
        storedToken.revoke();

        return generateTokenPair(user);
    }

    private void logRefreshTokenReuseIfRevoked(Long userId, String refreshTokenHash) {
        refreshTokenRepository.findByUserIdAndTokenHash(userId, refreshTokenHash)
                .filter(RefreshToken::isRevoked)
                .ifPresent(token -> log.warn(
                        "Revoked refresh token reuse detected. userId={}, refreshTokenId={}",
                        userId,
                        token.getRefreshTokenId()
                ));
    }

    /**
     * 회원가입 처리 - 기존 사용자의 추가 정보 업데이트
     */
    public void signup(SignupRequestDto signupRequest) {
        // 현재 인증된 사용자 조회
        User user = getCurrentUser();

        // 이미 회원가입 완료된 사용자면 예외 처리
        if (!UserStatus.GUEST.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.SIGNUP_FORBIDDEN);
        }

        // 지역 조회
        Region region = regionRepository.findById(signupRequest.getRegionId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        // 사용자 정보 업데이트
        user.updateSignupInfo(
                region,
                signupRequest.getNickname(),
                signupRequest.getBirth(),
                signupRequest.getGender()
        );

        // 회원가입 완료 처리
        user.completeSignup();
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDto getMyProfile() {
        User user = getCurrentUser();
        Region region = user.getRegion();

        return new UserProfileResponseDto(
                user.getUserId(),
                region != null ? region.getCity() : null,
                region != null ? region.getDistrict() : null,
                user.getNickname(),
                user.getBirth(),
                user.getGender(),
                user.getEngineer() != null
        );
    }

    @Transactional(readOnly = true)
    public EngineerRegistrationStatusResponseDto getEngineerRegistrationStatus() {
        User user = getCurrentUser();
        boolean engineerRegistered = user.getEngineer() != null;
        Long engineerId = engineerRegistered ? user.getEngineer().getEngineerId() : null;
        return new EngineerRegistrationStatusResponseDto(engineerRegistered, engineerId);
    }

    public void updateMyProfile(UpdateUserProfileRequestDto requestDto) {
        User user = getCurrentUser();
        Region region = regionRepository.findById(requestDto.getRegionId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        user.updateSignupInfo(
                region,
                requestDto.getNickname(),
                requestDto.getBirth(),
                requestDto.getGender()
        );
    }

    /**
     * 로그아웃 처리 - 카카오 액세스 토큰 제거 및 refreshToken 제거
     */
    public void logoutUser() {
        User user = getCurrentUser();
        refreshTokenRepository.revokeAllActiveByUserId(user.getUserId(), LocalDateTime.now());
        if (user.getKakaoAccessToken() != null) {
            user.clearKakaoAccessToken();
            userRepository.save(user);
        }
    }

    /**
     * 회원 탈퇴 처리 - 사용자 상태를 WITHDRAWN으로 변경하고 즉시 익명화 처리
     */
    public void withdrawUser() {
        User user = getCurrentUser();
        refreshTokenRepository.revokeAllActiveByUserId(user.getUserId(), LocalDateTime.now());
        user.withdraw();
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<UserListItemResponseDto> getAdminUsers(String keyword, Pageable pageable) {
        validateAdmin(getCurrentUser());
        String normalizedKeyword = normalizeAdminUserSearchKeyword(keyword);
        Page<User> users;
        if (normalizedKeyword == null) {
            users = userRepository.findAllForAdmin(pageable);
        } else if (isUserIdKeyword(normalizedKeyword)) {
            users = userRepository.findByUserIdForAdmin(Long.valueOf(normalizedKeyword), pageable);
        } else {
            users = userRepository.searchByNicknameForAdmin(normalizedKeyword, pageable);
        }

        return users
                .map(UserListItemResponseDto::from);
    }

    public AdminVerificationResponseDto verifyAdminPassword(AdminVerificationRequestDto request) {
        User admin = getCurrentUser();
        validateAdmin(admin);
        if (adminVerificationPasswordHash == null || adminVerificationPasswordHash.isBlank()
                || !passwordEncoder.matches(request.getPassword(), adminVerificationPasswordHash)) {
            throw new CustomException(ErrorCode.ADMIN_VERIFICATION_FAILED);
        }

        return new AdminVerificationResponseDto(generateAdminVerifiedToken(admin));
    }

    private String generateAdminVerifiedToken(User admin) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + adminVerificationTokenExpiration);
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(admin.getUserId().toString())
                .claim("type", "admin_verified")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    private String normalizeAdminUserSearchKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }

    private boolean isUserIdKeyword(String keyword) {
        try {
            Long.parseLong(keyword);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void suspendUserByAdmin(Long userId) {
        validateAdmin(getCurrentUser());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (UserStatus.WITHDRAWN.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            return;
        }
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_INVALID_STATUS);
        }

        user.suspend();
        refreshTokenRepository.revokeAllActiveByUserId(user.getUserId(), LocalDateTime.now());
    }

    public void activateUserByAdmin(Long userId) {
        validateAdmin(getCurrentUser());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (UserStatus.WITHDRAWN.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }
        if (UserStatus.ACTIVE.equals(user.getStatus())) {
            return;
        }
        if (!UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_INVALID_STATUS);
        }

        user.activate();
    }

    private void validateAdmin(User user) {
        if (!UserRole.ADMIN.equals(user.getRole())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void rotateRefreshToken(User user, String refreshToken) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getUserId(), LocalDateTime.now());
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpiration));
        String refreshTokenHash = hashRefreshToken(refreshToken);
        refreshTokenRepository.save(RefreshToken.issue(user, refreshTokenHash, expiresAt));
    }

    private String hashRefreshToken(String refreshToken) {
        try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte hashedByte : hashedBytes) {
                sb.append(String.format("%02x", hashedByte));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
