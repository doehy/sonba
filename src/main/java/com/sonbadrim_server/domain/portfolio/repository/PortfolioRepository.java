package com.sonbadrim_server.domain.portfolio.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    void deleteAllByEngineer(Engineer engineer);

    List<Portfolio> findByEngineer_EngineerId(Long engineerId);

    @Query("""
            select p.portfolioImage
            from Portfolio p
            where p.portfolioImage is not null
              and p.portfolioImage <> ''
            """)
    List<String> findAllUsedPortfolioImageUrls();

    @Query("""
            select p.portfolioThumbnailImage
            from Portfolio p
            where p.portfolioThumbnailImage is not null
              and p.portfolioThumbnailImage <> ''
            """)
    List<String> findAllUsedPortfolioThumbnailImageUrls();

    @Modifying
    @Query("""
            update Portfolio p
            set p.portfolioThumbnailImage = :thumbnailImage
            where p.portfolioId = :portfolioId
              and p.portfolioImage = :originalPortfolioImage
            """)
    int updatePortfolioThumbnailImageIfPortfolioUnchanged(
            @Param("portfolioId") Long portfolioId,
            @Param("originalPortfolioImage") String originalPortfolioImage,
            @Param("thumbnailImage") String thumbnailImage
    );
}
