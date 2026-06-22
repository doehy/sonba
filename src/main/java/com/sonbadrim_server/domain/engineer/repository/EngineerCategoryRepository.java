package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EngineerCategoryRepository extends JpaRepository<EngineerCategory, Long> {

    void deleteAllByEngineer(Engineer engineer);

    @EntityGraph(attributePaths = {"engineer", "category"})
    List<EngineerCategory> findByEngineer_EngineerIdIn(List<Long> engineerIds);
}




