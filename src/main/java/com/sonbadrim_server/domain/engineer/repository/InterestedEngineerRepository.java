package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestedEngineerRepository extends JpaRepository<InterestedEngineer, Long> {

    boolean existsByUser_UserIdAndEngineer_EngineerId(Long userId, Long engineerId);

    void deleteByUser_UserIdAndEngineer_EngineerId(Long userId, Long engineerId);

    void deleteAllByEngineer(Engineer engineer);

    @EntityGraph(attributePaths = {"engineer"})
    Page<InterestedEngineer> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
