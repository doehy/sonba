package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface EngineerSearchRepository {

    Slice<Engineer> searchEngineers(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    );
}
