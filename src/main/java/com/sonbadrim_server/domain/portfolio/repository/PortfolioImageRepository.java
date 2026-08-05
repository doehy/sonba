package com.sonbadrim_server.domain.portfolio.repository;

import com.sonbadrim_server.domain.portfolio.entity.PortfolioImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioImageRepository extends JpaRepository<PortfolioImage, Long> {

    @Query("""
            select pi.imageUrl
            from PortfolioImage pi
            where pi.imageUrl is not null
              and pi.imageUrl <> ''
            """)
    List<String> findAllUsedImageUrls();

    @Query("""
            select pi.thumbnailImageUrl
            from PortfolioImage pi
            where pi.thumbnailImageUrl is not null
              and pi.thumbnailImageUrl <> ''
            """)
    List<String> findAllUsedThumbnailImageUrls();

    @Modifying
    @Query("""
            update PortfolioImage pi
            set pi.thumbnailImageUrl = :thumbnailImageUrl
            where pi.portfolioImageId = :portfolioImageId
              and pi.imageUrl = :originalImageUrl
            """)
    int updateThumbnailImageIfImageUnchanged(
            @Param("portfolioImageId") Long portfolioImageId,
            @Param("originalImageUrl") String originalImageUrl,
            @Param("thumbnailImageUrl") String thumbnailImageUrl
    );
}
