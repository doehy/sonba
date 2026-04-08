package com.sonbadrim_server.domain.category.repository;

import com.sonbadrim_server.domain.category.entity.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByCategoryNameIn(Collection<String> categoryNames);

    Optional<Category> findByCategoryName(String name);
}
