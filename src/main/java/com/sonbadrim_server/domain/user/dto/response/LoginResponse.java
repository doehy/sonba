package com.sonbadrim_server.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private AuthTokenResponse token;
    private boolean requiresSignup;
}

