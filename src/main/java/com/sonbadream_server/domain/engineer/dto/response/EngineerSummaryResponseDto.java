package com.sonbadream_server.domain.engineer.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerSummaryResponseDto {

    private Long engineerId;
    private String engineerName;
    private String profileImage;
    private List<String> categories;
    private String introduction;
}
