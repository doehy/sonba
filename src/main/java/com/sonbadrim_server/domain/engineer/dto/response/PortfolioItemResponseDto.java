package com.sonbadrim_server.domain.engineer.dto.response;

import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.portfolio.entity.PortfolioType;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PortfolioItemResponseDto {

    private Long portfolioId;
    private PortfolioType type;
    private String title;
    private List<String> images;
    private List<String> thumbnailImages;

    public static PortfolioItemResponseDto from(Portfolio portfolio) {
        return PortfolioItemResponseDto.builder()
                .portfolioId(portfolio.getPortfolioId())
                .type(portfolio.getType())
                .title(portfolio.getTitle())
                .images(portfolio.getImages().stream()
                        .map(portfolioImage -> portfolioImage.getImageUrl())
                        .toList())
                .thumbnailImages(portfolio.getImages().stream()
                        .map(portfolioImage -> portfolioImage.getDisplayImageUrl())
                        .toList())
                .build();
    }
}
