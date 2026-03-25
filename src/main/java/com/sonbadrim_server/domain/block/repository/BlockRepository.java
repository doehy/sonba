package com.sonbadrim_server.domain.block.repository;

import com.sonbadrim_server.domain.block.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, Long> {


}
