package com.sonbadrim_server.domain.block.repository;

import com.sonbadrim_server.domain.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlocker_UserIdAndBlocked_UserId(Long blockerUserId, Long blockedUserId);

    void deleteByBlocker_UserIdAndBlocked_UserId(Long blockerUserId, Long blockedUserId);
}
