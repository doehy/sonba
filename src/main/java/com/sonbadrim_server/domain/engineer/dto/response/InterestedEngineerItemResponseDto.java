package com.sonbadrim_server.domain.engineer.dto.response;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InterestedEngineerItemResponseDto {
    private Long engineerId;
    private String engineerName;
    private String profileImage;

    public static InterestedEngineerItemResponseDto from(Engineer engineer) {
        return new InterestedEngineerItemResponseDto(
                engineer.getEngineerId(),
                engineer.getEngineerName(),
                engineer.getDisplayProfileImage()
        );
    }
}
