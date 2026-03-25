package com.sonbadrim_server.domain.portfolio.repository;

import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {


}
