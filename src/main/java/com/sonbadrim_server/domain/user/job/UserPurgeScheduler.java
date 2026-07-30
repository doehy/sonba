package com.sonbadrim_server.domain.user.job;

import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurgeScheduler {

    private final UserRepository userRepository;

    @Value("${user.withdraw-retention-days:90}")
    private long withdrawRetentionDays;

    /**
     * Withdrawn users are physically deleted after retention period.
     * Current MVP deletes only rows without FK-dependent records.
     */
    @Transactional
    @Scheduled(cron = "${scheduler.user-purge.cron:0 0 3 * * SUN}")
    public void purgeWithdrawnUsers() {
        LocalDate today = LocalDate.now();
        if (!isLastSundayOfMonth(today)) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(withdrawRetentionDays);

        List<User> purgeCandidates = userRepository.findPurgeCandidates(UserStatus.WITHDRAWN, cutoff);
        if (purgeCandidates.isEmpty()) {
            return;
        }

        userRepository.deleteAllInBatch(purgeCandidates);
        log.info("Purged withdrawn users count={}", purgeCandidates.size());
    }

    private boolean isLastSundayOfMonth(LocalDate date) {
        if (date.getDayOfWeek().getValue() != 7) {
            return false;
        }
        return date.plusWeeks(1).getMonth() != date.getMonth();
    }
}
