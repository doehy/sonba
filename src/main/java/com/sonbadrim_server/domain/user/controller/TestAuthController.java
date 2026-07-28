package com.sonbadrim_server.domain.user.controller;

import com.sonbadrim_server.domain.user.dto.request.TestLoginRequestDto;
import com.sonbadrim_server.domain.user.dto.response.AuthTokenResponse;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.common.CommonResponse;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Profile({"local", "test"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class TestAuthController {

    private final UserRepository userRepository;
    private final UserService userService;

    @Value("${app.test-login.password:test1234}")
    private String testLoginPassword;

    @PostMapping("/test-login")
    public ResponseEntity<CommonResponse<AuthTokenResponse>> testLogin(
            @Valid @RequestBody TestLoginRequestDto request
    ) {
        if (!testLoginPassword.equals(request.getPassword())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (UserStatus.WITHDRAWN.equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_WITHDRAWN);
        }

        Map<String, String> tokens = userService.generateTokenPair(user);
        AuthTokenResponse response = new AuthTokenResponse(
                tokens.get("accessToken"),
                tokens.get("refreshToken")
        );
        return ResponseEntity.ok(CommonResponse.success(response));
    }
}
