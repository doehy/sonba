package com.sonbadrim_server.domain.portfolio.entity;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.sonbadrim_server.global.BaseTimeEntity;
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

    @Column(name = "portfolioImage", nullable = false, length = 255)
    private String portfolioImage;

    public static Portfolio create(Engineer engineer, String portfolioImage) {
        Portfolio portfolio = new Portfolio();
        portfolio.engineer = engineer;
        portfolio.portfolioImage = portfolioImage;
        return portfolio;
    }
}
