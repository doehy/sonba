package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.Inquiry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByRequesterIdAndTargetId(Long requesterId, Long targetId);

    default Optional<Inquiry> findPairInquiry(Long userId1, Long userId2) {
        return findByRequesterIdAndTargetId(userId1, userId2)
                .or(() -> findByRequesterIdAndTargetId(userId2, userId1));
    }

    Page<Inquiry> findByRequesterIdOrTargetIdOrderByLastMessageAtDescIdDesc(Long requesterId, Long targetId, Pageable pageable);
}
