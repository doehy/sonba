package com.sonbadrim_server.domain.block.repository;

import com.sonbadrim_server.domain.block.Block;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<Block> findByBlockerId(Long blockerId);
}
