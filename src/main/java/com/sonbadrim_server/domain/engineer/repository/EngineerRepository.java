package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, Long> {

    boolean existsByUser_UserId(Long userId);

    @EntityGraph(attributePaths = {"region"})
    Page<Engineer> findAll(Pageable pageable);

    @Query("""
    select distinct e
    from Engineer e
    join fetch e.region r
    left join fetch e.engineerCategories ec
    left join fetch ec.category c
    where e.engineerId = :engineerId
""")
    Optional<Engineer> findDetailByEngineerId(Long engineerId);

    Optional<Engineer> findByEngineerIdAndUser_UserId(Long engineerId, Long userId);
}
