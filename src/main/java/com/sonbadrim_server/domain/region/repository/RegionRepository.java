package com.sonbadrim_server.domain.region.repository;

import com.sonbadrim_server.domain.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Integer> {
    List<Region> findAllByOrderByRegionIdAsc();
}
