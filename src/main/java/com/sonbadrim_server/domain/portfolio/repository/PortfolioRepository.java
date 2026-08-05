package com.sonbadrim_server.domain.portfolio.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    void deleteAllByEngineer(Engineer engineer);

    @EntityGraph(attributePaths = {"images"})
    List<Portfolio> findByEngineer_EngineerIdOrderBySortOrderAscPortfolioIdAsc(Long engineerId);

    default List<Portfolio> findByEngineer_EngineerId(Long engineerId) {
        return findByEngineer_EngineerIdOrderBySortOrderAscPortfolioIdAsc(engineerId);
    }
}
