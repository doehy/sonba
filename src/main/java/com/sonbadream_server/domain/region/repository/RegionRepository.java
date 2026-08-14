package com.sonbadream_server.domain.region.repository;

import com.sonbadream_server.domain.region.entity.Region;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Integer> {
    List<Region> findAllByOrderByRegionIdAsc();

    @Query("""
            select distinct r.city
            from Region r
            where :keyword is not null
              and lower(r.city) like lower(concat('%', :keyword, '%'))
            order by r.city asc
            """)
    List<String> findCitySuggestions(String keyword, Pageable pageable);

    @Query("""
            select distinct r.district
            from Region r
            where :keyword is not null
              and lower(r.district) like lower(concat('%', :keyword, '%'))
            order by r.district asc
            """)
    List<String> findDistrictSuggestions(String keyword, Pageable pageable);
}
