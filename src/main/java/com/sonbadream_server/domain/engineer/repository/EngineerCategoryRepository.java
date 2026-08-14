package com.sonbadream_server.domain.engineer.repository;

import com.sonbadream_server.domain.engineer.entity.Engineer;
import com.sonbadream_server.domain.engineer.entity.EngineerCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EngineerCategoryRepository extends JpaRepository<EngineerCategory, Long> {

    void deleteAllByEngineer(Engineer engineer);

    @EntityGraph(attributePaths = {"engineer", "category"})
    List<EngineerCategory> findByEngineer_EngineerIdIn(List<Long> engineerIds);

    @Query("""
            select ec.category.categoryId
            from EngineerCategory ec
            where ec.engineer.engineerId = :engineerId
            """)
    List<Integer> findCategoryIdsByEngineerId(Long engineerId);
}



