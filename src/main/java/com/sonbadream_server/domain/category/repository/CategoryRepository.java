package com.sonbadream_server.domain.category.repository;

import com.sonbadream_server.domain.category.entity.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByCategoryNameIn(Collection<String> categoryNames);

    Optional<Category> findByCategoryName(String name);

    @Query("""
            select distinct c.categoryName
            from Category c
            where :keyword is not null
              and lower(c.categoryName) like lower(concat('%', :keyword, '%'))
            order by c.categoryName asc
            """)
    List<String> findCategoryNameSuggestions(String keyword, Pageable pageable);
}
