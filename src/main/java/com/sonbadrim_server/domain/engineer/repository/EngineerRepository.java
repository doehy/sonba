package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.engineer.Engineer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerRepository extends JpaRepository<Engineer, Long> {


}
