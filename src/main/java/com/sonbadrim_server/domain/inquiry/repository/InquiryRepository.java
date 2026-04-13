package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByRequester_UserIdAndTarget_UserId(Long requesterUserId, Long targetUserId);

    @EntityGraph(attributePaths = {"requester", "target", "requester.engineer", "target.engineer"})
    Page<Inquiry> findByRequester_UserIdOrTarget_UserIdOrderByLastMessageAtDesc(Long requesterUserId, Long targetUserId, Pageable pageable);

    @Query("""
            select i from Inquiry i
            where i.inquiryId = :inquiryId
              and (i.requester.userId = :userId or i.target.userId = :userId)
            """)
    Optional<Inquiry> findByInquiryIdAndParticipant(Long inquiryId, Long userId);
}
