package com.sonbadrim_server.domain.portfolio.entity;

import com.sonbadrim_server.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "portfolio_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_image_id")
    private Long portfolioImageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    @Column(name = "thumbnail_image_url", length = 512)
    private String thumbnailImageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static PortfolioImage create(Portfolio portfolio, String imageUrl, int sortOrder) {
        PortfolioImage portfolioImage = new PortfolioImage();
        portfolioImage.portfolio = portfolio;
        portfolioImage.imageUrl = imageUrl;
        portfolioImage.thumbnailImageUrl = null;
        portfolioImage.sortOrder = sortOrder;
        return portfolioImage;
    }

    public String getDisplayImageUrl() {
        return thumbnailImageUrl != null && !thumbnailImageUrl.isBlank()
                ? thumbnailImageUrl
                : imageUrl;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
