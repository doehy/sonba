package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.entity.InquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    List<InquiryMessage> findByInquiry_InquiryIdOrderByInquiryMessageIdAsc(Long inquiryId);

    List<InquiryMessage> findByInquiry_InquiryIdOrderByInquiryMessageIdDesc(Long inquiryId, Pageable pageable);

    List<InquiryMessage> findByInquiry_InquiryIdAndInquiryMessageIdLessThanOrderByInquiryMessageIdDesc(
            Long inquiryId,
            Long inquiryMessageId,
            Pageable pageable
    );

    List<InquiryMessage> findByInquiryMessageIdIn(Collection<Long> inquiryMessageIds);

    long countByInquiry_InquiryIdAndSender_UserIdNot(Long inquiryId, Long senderUserId);

    long countByInquiry_InquiryIdAndInquiryMessageIdGreaterThanAndSender_UserIdNot(
            Long inquiryId,
            Long inquiryMessageId,
            Long senderUserId
    );
}
