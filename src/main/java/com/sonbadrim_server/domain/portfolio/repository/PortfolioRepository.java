package com.sonbadrim_server.domain.portfolio.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    void deleteAllByEngineer(Engineer engineer);

    List<Portfolio> findByEngineer_EngineerId(Long engineerId);

    @Modifying
    void deleteByEngineer(Engineer engineer);

    @Query("""
            select p.portfolioImage
            from Portfolio p
            where p.portfolioImage is not null
              and p.portfolioImage <> ''
            """)
    List<String> findAllUsedPortfolioImageUrls();
}
