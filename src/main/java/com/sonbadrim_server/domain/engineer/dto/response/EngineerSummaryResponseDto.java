package com.sonbadrim_server.domain.engineer.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerSummaryResponseDto {

    private Long engineerId;
    private String headline;
    private String profileImage;
    private String regionName;
    private List<String> categories;
    private String introduction;
}
