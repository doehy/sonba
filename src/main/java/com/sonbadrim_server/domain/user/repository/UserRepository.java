package com.sonbadrim_server.domain.user.repository;

import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    Optional<User>findByUserId(Long userId);

    List<User> findByStatus(UserStatus status);

    @Query("""
            select u
            from User u
            where u.status = :status
              and u.withdrawnAt is not null
              and u.withdrawnAt < :cutoff
              and u.engineer is null
              and u.notifications is empty
              and u.blocksByMe is empty
              and u.blocksAgainstMe is empty
              and u.requestedInquiries is empty
              and u.targetedInquiries is empty
              and u.inquiryMessages is empty
            """)
    List<User> findPurgeCandidates(
            @Param("status") UserStatus status,
            @Param("cutoff") LocalDateTime cutoff
    );
}
