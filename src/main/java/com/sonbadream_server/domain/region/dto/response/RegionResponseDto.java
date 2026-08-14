package com.sonbadream_server.domain.region.dto.response;

import com.sonbadream_server.domain.region.entity.Region;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegionResponseDto {
    private Integer regionId;
    private String city;
    private String district;

    public static RegionResponseDto from(Region region) {
        return new RegionResponseDto(
                region.getRegionId(),
                region.getCity(),
                region.getDistrict()
        );
    }
}
