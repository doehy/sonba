package com.sonbadream_server.domain.engineer.entity;

import com.sonbadream_server.domain.category.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.sonbadream_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "engineer_category",
        indexes = {
            @Index(name = "idx_engineer_category_category_engineer", columnList = "category_id, engineer_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_engineer_category", columnNames = {"engineer_id", "category_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "engineer_category_id")
    private Long engineerCategoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engineer_id", nullable = false)
    private Engineer engineer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public static EngineerCategory create(Engineer engineer, Category category) {
        EngineerCategory engineerCategory = new EngineerCategory();
        engineerCategory.engineer = engineer;
        engineerCategory.category = category;
        return engineerCategory;
    }


}
