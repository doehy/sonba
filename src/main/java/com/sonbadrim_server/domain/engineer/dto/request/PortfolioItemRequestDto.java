package com.sonbadrim_server.domain.engineer.dto.request;

import com.sonbadrim_server.domain.portfolio.entity.PortfolioType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PortfolioItemRequestDto {

    @Positive(message = "포트폴리오 ID는 양수여야 합니다.")
    private Long portfolioId;

    @NotNull(message = "포트폴리오 타입은 필수입니다.")
    private PortfolioType type;

    @Size(max = 50, message = "포트폴리오 제목은 50자 이하로 입력해주세요.")
    private String title;

    @NotEmpty(message = "포트폴리오 이미지는 최소 1장 이상 필요합니다.")
    @Size(max = 8, message = "포트폴리오 이미지는 시공 사례당 최대 8장까지 등록 가능합니다.")
    private List<@Size(max = 512, message = "포트폴리오 이미지 URL은 512자 이하로 입력해주세요.") String> images;
}
