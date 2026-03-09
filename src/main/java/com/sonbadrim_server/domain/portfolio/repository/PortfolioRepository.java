package com.sonbadrim_server.domain.portfolio.repository;

import com.sonbadrim_server.domain.portfolio.Portfolio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByEngineerId(Long engineerId);

    void deleteByEngineerId(Long engineerId);
}
