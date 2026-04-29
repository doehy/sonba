package com.sonbadrim_server.domain.user.job;

import com.sonbadrim_server.domain.user.repository.RefreshTokenRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(cron = "0 0 3 * * SAT")
    public void cleanupRefreshTokens() {
        if (!isLastSaturdayOfMonth(LocalDate.now())) {
            return;
        }

        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Deleted {} refresh tokens (expired or revoked).", deleted);
        }
    }

    private boolean isLastSaturdayOfMonth(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            return false;
        }
        return date.plusWeeks(1).getMonth() != date.getMonth();
    }
}
