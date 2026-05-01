package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestedEngineerRepository extends JpaRepository<InterestedEngineer, Long> {

    boolean existsByUser_UserIdAndEngineer_EngineerId(Long userId, Long engineerId);

    void deleteByUser_UserIdAndEngineer_EngineerId(Long userId, Long engineerId);
}
