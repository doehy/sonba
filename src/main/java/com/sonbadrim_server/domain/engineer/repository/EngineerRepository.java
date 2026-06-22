package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, Long> {

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

    @Query(
            value = """
            select distinct e
            from Engineer e
            join e.region r
            left join e.engineerCategories ec
            left join ec.category c
            where (
                    :keyword is null
                    or lower(e.engineerName) like lower(concat('%', :keyword, '%'))
                    or lower(e.headline) like lower(concat('%', :keyword, '%'))
            )
            and (:city is null or r.city = :city)
            and (:regionId is null or r.regionId = :regionId)
            and (:categoryIds is null or c.categoryId in :categoryIds)
            """,
            countQuery = """
            select count(distinct e)
            from Engineer e
            join e.region r
            left join e.engineerCategories ec
            left join ec.category c
            where (
                    :keyword is null
                    or lower(e.engineerName) like lower(concat('%', :keyword, '%'))
                    or lower(e.headline) like lower(concat('%', :keyword, '%'))
            )
            and (:city is null or r.city = :city)
            and (:regionId is null or r.regionId = :regionId)
            and (:categoryIds is null or c.categoryId in :categoryIds)
            """
    )
    Page<Engineer> searchEngineers(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    );

    @Query("""
            select e
            from Engineer e
            where e.engineerId <> :baseEngineerId
            order by
                (
                    select count(ec)
                    from EngineerCategory ec
                    where ec.engineer = e
                      and ec.category.categoryId in (
                          select bec.category.categoryId
                          from EngineerCategory bec
                          where bec.engineer.engineerId = :baseEngineerId
                      )
                ) desc,
                case
                    when e.region.regionId = (
                        select b.region.regionId
                        from Engineer b
                        where b.engineerId = :baseEngineerId
                    ) then 1
                    else 0
                end desc,
                e.createdAt desc
            """)
    Slice<Engineer> findRecommendedEngineersByBaseEngineerId(Long baseEngineerId, Pageable pageable);




    @Query("""
            select distinct e.engineerName
            from Engineer e
            where :keyword is not null
              and lower(e.engineerName) like lower(concat('%', :keyword, '%'))
            order by e.engineerName asc
            """)
    List<String> findEngineerNameSuggestions(String keyword, Pageable pageable);

    @Query("""
            select distinct e.headline
            from Engineer e
            where :keyword is not null
              and e.headline is not null
              and e.headline <> ''
              and lower(e.headline) like lower(concat('%', :keyword, '%'))
            order by e.headline asc
            """)
    List<String> findHeadlineSuggestions(String keyword, Pageable pageable);

    @Query("""
            select e.profileImage
            from Engineer e
            where e.profileImage is not null
              and e.profileImage <> ''
            """)
    List<String> findAllUsedProfileImageUrls();

    boolean existsByPhoneAndEngineerIdNot(String phone, Long engineerId);
}
