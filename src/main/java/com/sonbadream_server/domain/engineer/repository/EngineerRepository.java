package com.sonbadream_server.domain.engineer.repository;

import com.sonbadream_server.domain.engineer.entity.Engineer;
import com.sonbadream_server.domain.engineer.entity.EngineerClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, Long>, EngineerSearchRepository, EngineerRecommendationRepository {

    boolean existsByUser_UserId(Long userId);

    boolean existsByPhone(String phone);

    @EntityGraph(attributePaths = {"claimRequester", "region"})
    Page<Engineer> findByClaimStatusOrderByModifiedAtDesc(EngineerClaimStatus claimStatus, Pageable pageable);

    @Query("""
            select e
            from Engineer e
            """)
    Slice<Engineer> findEngineerList(Pageable pageable);

    @Query("""
    select distinct e
    from Engineer e
    left join fetch e.user u
    join fetch e.region r
    left join fetch e.engineerCategories ec
    left join fetch ec.category c
    where e.engineerId = :engineerId
""")
    Optional<Engineer> findDetailByEngineerId(Long engineerId);

    @Query("""
            select e.region.regionId as regionId,
                   e.region.city as city
            from Engineer e
            where e.engineerId = :engineerId
            """)
    Optional<EngineerRecommendationBaseInfo> findRecommendationBaseInfoByEngineerId(Long engineerId);

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

    @Query("""
            select e.profileThumbnailImage
            from Engineer e
            where e.profileThumbnailImage is not null
              and e.profileThumbnailImage <> ''
            """)
    List<String> findAllUsedProfileThumbnailImageUrls();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Engineer e
            set e.profileThumbnailImage = :thumbnailImage
            where e.engineerId = :engineerId
              and e.profileImage = :originalProfileImage
            """)
    int updateProfileThumbnailImageIfProfileUnchanged(
            @Param("engineerId") Long engineerId,
            @Param("originalProfileImage") String originalProfileImage,
            @Param("thumbnailImage") String thumbnailImage
    );

    boolean existsByPhoneAndEngineerIdNot(String phone, Long engineerId);
}
