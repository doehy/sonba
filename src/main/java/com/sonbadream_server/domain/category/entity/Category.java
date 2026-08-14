package com.sonbadream_server.domain.category.entity;

import com.sonbadream_server.domain.engineer.entity.EngineerCategory;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import com.sonbadream_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "category",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_category_name", columnNames = {"category_name"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name", nullable = false, length = 20)
    private String categoryName;

    @OneToMany(mappedBy = "category")
    private List<EngineerCategory> engineerCategories = new ArrayList<>();
}
