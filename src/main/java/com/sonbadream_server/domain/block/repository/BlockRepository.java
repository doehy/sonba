package com.sonbadream_server.domain.block.repository;

import com.sonbadream_server.domain.block.entity.Block;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlocker_UserIdAndBlocked_UserId(Long blockerUserId, Long blockedUserId);

    void deleteByBlocker_UserIdAndBlocked_UserId(Long blockerUserId, Long blockedUserId);

    @EntityGraph(attributePaths = {"blocked", "blocked.engineer"})
    Page<Block> findByBlocker_UserIdOrderByCreatedAtDesc(Long blockerUserId, Pageable pageable);
}
