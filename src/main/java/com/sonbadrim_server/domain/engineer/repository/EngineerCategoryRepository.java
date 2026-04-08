package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerCategoryRepository extends JpaRepository<EngineerCategory, Long> {
    void deleteAllByEngineer(Engineer engineer);
}
