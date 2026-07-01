package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.entity.Inquiry;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByRequester_UserIdAndTarget_UserId(Long requesterUserId, Long targetUserId);

    @EntityGraph(attributePaths = {"requester", "target", "requester.engineer", "target.engineer"})
    @Query("""
            select i
            from Inquiry i
            where (i.requester.userId = :userId and i.requesterLeftAt is null)
               or (i.target.userId = :userId and i.targetLeftAt is null)
            order by i.lastMessageAt desc
            """)
    Page<Inquiry> findVisibleByUserId(Long userId, Pageable pageable);

    @Query("""
            select i from Inquiry i
            where i.inquiryId = :inquiryId
              and (i.requester.userId = :userId or i.target.userId = :userId)
            """)
    Optional<Inquiry> findByInquiryIdAndParticipant(Long inquiryId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Inquiry i
            where i.inquiryId = :inquiryId
            """)
    Optional<Inquiry> findByIdForUpdate(Long inquiryId);
}
