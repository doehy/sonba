package com.sonbadream_server.domain.engineer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PhoneDuplicateCheckResponseDto {
    private boolean duplicated;
}
