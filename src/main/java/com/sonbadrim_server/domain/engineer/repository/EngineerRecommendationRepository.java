package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface EngineerRecommendationRepository {

    Slice<Engineer> findRecommendedEngineers(
            Long baseEngineerId,
            Integer baseRegionId,
            String baseCity,
            List<Integer> baseCategoryIds,
            Pageable pageable
    );
}
