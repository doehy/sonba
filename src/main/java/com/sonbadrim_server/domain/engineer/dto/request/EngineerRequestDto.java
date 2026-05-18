package com.sonbadrim_server.domain.engineer.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class EngineerRequestDto {

    @NotEmpty(message = "시공분야는 최소 1개 이상 선택해야 합니다.")
    @Size(max = 5, message = "시공분야는 최대 5개까지 선택 가능합니다.")
    private List<@NotBlank(message = "시공분야 값은 비어 있을 수 없습니다.") String> categories;

    @NotEmpty(message = "포트폴리오 이미지는 최소 1장 이상 필요합니다.")
    @Size(max = 6, message = "포트폴리오 이미지는 최대 6장까지 등록 가능합니다.")
    private List<@NotBlank(message = "포트폴리오 이미지 값은 비어 있을 수 없습니다.") String> portfolioImages;

    @Size(max = 300, message = "자기소개는 300자 이하로 입력해주세요.")
    private String introduction;

    @NotNull(message = "활동 지역은 필수입니다.")
    private Integer regionId;

    private String profileImage;

    @Size(max = 20, message = "가게 이름은 20자 이하로 적어주세요.")
    private String storeName;

    @Size(max = 30, message = "가게 주소는 30자 이하로 입력해주세요.")
    private String storeAddress;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Size(max = 20, message = "전화번호는 20자 이하로 입력해주세요.")
    @Pattern(
            regexp = "^(0\\d{1,2}-?\\d{3,4}-?\\d{4})$",
            message = "전화번호 형식이 올바르지 않습니다."
    )
    private String phone;

    @NotNull(message = "전화번호 공개 여부는 필수입니다.")
    private Boolean phonePublic;

    @NotBlank(message = "기술자 닉네임은 필수입니다.")
    @Size(max = 20, message = "기술자 닉네임은 20자 이하로 입력해주세요.")
    private String engineerName;

    @Size(max = 100, message = "짧은 소개는 100자 이하로 입력해주세요.")
    private String headline;
}
