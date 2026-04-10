package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByRequester_UserIdAndTarget_UserId(Long requesterUserId, Long targetUserId);
}
