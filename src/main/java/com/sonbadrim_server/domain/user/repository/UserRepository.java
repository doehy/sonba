package com.sonbadrim_server.domain.user.repository;

import com.sonbadrim_server.domain.user.User;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    Optional<User>findByUserId(Long userId);
}
