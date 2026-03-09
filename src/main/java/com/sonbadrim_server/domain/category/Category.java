package com.sonbadrim_server.domain.category;

import com.sonbadrim_server.domain.engineer.EngineerCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @Column(name = "category_id")
    private Integer id;

    @Column(name = "category_name", nullable = false, length = 20)
    private String categoryName;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "category")
    private List<EngineerCategory> engineerCategories = new ArrayList<>();
}
