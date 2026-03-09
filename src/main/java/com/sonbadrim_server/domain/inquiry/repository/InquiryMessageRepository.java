package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.InquiryMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    Page<InquiryMessage> findByInquiryIdOrderByIdDesc(Long inquiryId, Pageable pageable);

    Page<InquiryMessage> findByInquiryIdAndIdLessThanOrderByIdDesc(Long inquiryId, Long cursorMessageId, Pageable pageable);

    long countByInquiryIdAndIdGreaterThanAndSenderIdNot(Long inquiryId, Long messageId, Long senderId);

    long countByInquiryIdAndSenderIdNot(Long inquiryId, Long senderId);
}
