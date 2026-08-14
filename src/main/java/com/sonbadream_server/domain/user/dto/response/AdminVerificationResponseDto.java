package com.sonbadream_server.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminVerificationResponseDto {

    private String adminVerifiedToken;
}
