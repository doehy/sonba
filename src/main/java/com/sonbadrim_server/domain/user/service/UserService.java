package com.sonbadrim_server.domain.user.service;

import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.entity.RefreshToken;
import com.sonbadrim_server.domain.user.dto.request.SignupRequestDto;
import com.sonbadrim_server.domain.user.dto.request.UpdateUserProfileRequestDto;
import com.sonbadrim_server.domain.user.dto.response.UserProfileResponseDto;
import com.sonbadrim_server.domain.user.repository.RefreshTokenRepository;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpiration;


    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        log.info("authentication = {}", authentication);
        log.info("name = {}", authentication.getName());
        log.info("principal = {}", authentication.getPrincipal());
        log.info("authenticated = {}", authentication.isAuthenticated());

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

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("type", "refresh")
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

        String refreshTokenHash = hashRefreshToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findValidToken(userId, refreshTokenHash, LocalDateTime.now())
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        storedToken.revoke();

        return generateTokenPair(user);
    }

    /**
     * 회원가입 처리 - 기존 사용자의 추가 정보 업데이트
     */
    public void signup(SignupRequestDto signupRequest) {
        // 현재 인증된 사용자 조회
        User user = getCurrentUser();

        // 이미 회원가입 완료된 사용자면 예외 처리
        if (!UserStatus.GUEST.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.ALREADY_SIGNED_UP_USER);
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
