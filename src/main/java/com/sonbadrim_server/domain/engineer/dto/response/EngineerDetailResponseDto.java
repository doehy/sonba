package com.sonbadrim_server.domain.engineer.dto.response;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerDetailResponseDto {

    private Long engineerId;
    private String engineerName;
    private String profileImage;
    private String regionName;
    private List<String> categories;
    private String introduction;
    private String storeName;
    private String storeAddress;
    private String phone;
    private boolean phonePublic;
    private List<String> portfolioImages;

    public static EngineerDetailResponseDto from(Engineer engineer, List<String> portfolioImages) {
        return EngineerDetailResponseDto.builder()
                .engineerId(engineer.getEngineerId())
                .engineerName(engineer.getEngineerName())
                .profileImage(engineer.getProfileImage())
                .regionName(engineer.getRegion().getCity() + " " + engineer.getRegion().getDistrict())
                .categories(
                        engineer.getEngineerCategories().stream()
                                .map(engineerCategory -> engineerCategory.getCategory().getCategoryName())
                                .distinct()
                                .toList()
                )
                .introduction(engineer.getIntroduction())
                .storeName(engineer.getStoreName())
                .storeAddress(engineer.getStoreAddress())
                .phone(engineer.getPhone())
                .phonePublic(engineer.isPhonePublic())
                .portfolioImages(portfolioImages)
                .build();
    }
}
