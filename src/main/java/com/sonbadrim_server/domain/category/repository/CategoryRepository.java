package com.sonbadrim_server.domain.category.repository;

import com.sonbadrim_server.domain.category.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByActiveTrue();
}
