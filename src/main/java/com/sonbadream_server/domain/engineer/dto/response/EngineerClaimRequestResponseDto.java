package com.sonbadream_server.domain.engineer.dto.response;

import com.sonbadream_server.domain.engineer.entity.Engineer;
import com.sonbadream_server.domain.engineer.entity.EngineerClaimStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerClaimRequestResponseDto {

    private Long engineerId;
    private String engineerName;
    private String phone;
    private String regionName;
    private EngineerClaimStatus claimStatus;
    private Long requesterUserId;
    private String requesterNickname;
    private LocalDateTime requestedAt;

    public static EngineerClaimRequestResponseDto from(Engineer engineer) {
        return EngineerClaimRequestResponseDto.builder()
                .engineerId(engineer.getEngineerId())
                .engineerName(engineer.getEngineerName())
                .phone(engineer.getPhone())
                .regionName(engineer.getRegion().getCity() + " " + engineer.getRegion().getDistrict())
                .claimStatus(engineer.getClaimStatus())
                .requesterUserId(engineer.getClaimRequester().getUserId())
                .requesterNickname(engineer.getClaimRequester().getNickname())
                .requestedAt(engineer.getModifiedAt())
                .build();
    }
}
