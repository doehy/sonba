package com.sonbadream_server.domain.portfolio.entity;

import com.sonbadream_server.domain.engineer.entity.Engineer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import com.sonbadream_server.global.BaseTimeEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "portfolio")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engineer_id", nullable = false)
    private Engineer engineer;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PortfolioType type;

    @Column(name = "title", length = 50)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, portfolioImageId ASC")
    private List<PortfolioImage> images = new ArrayList<>();

    public static Portfolio create(
            Engineer engineer,
            PortfolioType type,
            String title,
            int sortOrder,
            List<String> imageUrls
    ) {
        Portfolio portfolio = new Portfolio();
        portfolio.engineer = engineer;
        portfolio.type = type;
        portfolio.title = title;
        portfolio.sortOrder = sortOrder;
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                portfolio.addImage(imageUrls.get(i), i);
            }
        }
        return portfolio;
    }

    public void update(PortfolioType type, String title, int sortOrder) {
        this.type = type;
        this.title = title;
        this.sortOrder = sortOrder;
    }

    public PortfolioImage addImage(String imageUrl, int sortOrder) {
        PortfolioImage portfolioImage = PortfolioImage.create(this, imageUrl, sortOrder);
        images.add(portfolioImage);
        return portfolioImage;
    }

    public void removeImagesNotIn(Set<String> requestedImageUrls) {
        images.removeIf(image -> !requestedImageUrls.contains(image.getImageUrl()));
    }
}
