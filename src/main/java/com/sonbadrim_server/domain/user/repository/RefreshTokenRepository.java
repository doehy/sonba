package com.sonbadrim_server.domain.user.repository;

import com.sonbadrim_server.domain.user.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("""
            select rt
            from RefreshToken rt
            where rt.user.userId = :userId
              and rt.token = :token
              and rt.revokedAt is null
              and rt.expiresAt > :now
            """)
    Optional<RefreshToken> findValidToken(
            @Param("userId") Long userId,
            @Param("token") String token,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.user.userId = :userId
              and rt.revokedAt is null
            """)
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            delete from RefreshToken rt
            where rt.expiresAt < :now
               or rt.revokedAt is not null
            """)
    int deleteExpiredOrRevoked(@Param("now") LocalDateTime now);
}
