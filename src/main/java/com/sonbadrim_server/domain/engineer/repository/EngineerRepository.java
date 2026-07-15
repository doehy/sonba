package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, Long>, EngineerSearchRepository, EngineerRecommendationRepository {

    boolean existsByUser_UserId(Long userId);

    boolean existsByPhone(String phone);

    @EntityGraph(attributePaths = {"region"})
    Page<Engineer> findAll(Pageable pageable);

    @Query("""
    select distinct e
    from Engineer e
    join fetch e.user u
    join fetch e.region r
    left join fetch e.engineerCategories ec
    left join fetch ec.category c
    where e.engineerId = :engineerId
""")
    Optional<Engineer> findDetailByEngineerId(Long engineerId);

    @Query("""
            select e.region.regionId
            from Engineer e
            where e.engineerId = :engineerId
            """)
    Optional<Integer> findRegionIdByEngineerId(Long engineerId);

    @Query("""
            select distinct e.engineerName
            from Engineer e
            where :keyword is not null
              and lower(e.engineerName) like lower(concat('%', :keyword, '%'))
            order by e.engineerName asc
            """)
    List<String> findEngineerNameSuggestions(String keyword, Pageable pageable);

    @Query("""
            select e.profileImage
            from Engineer e
            where e.profileImage is not null
              and e.profileImage <> ''
            """)
    List<String> findAllUsedProfileImageUrls();

    boolean existsByPhoneAndEngineerIdNot(String phone, Long engineerId);
}
