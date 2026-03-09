package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.InquiryMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    List<InquiryMessage> findTop30ByInquiryIdOrderByIdDesc(Long inquiryId);

    List<InquiryMessage> findTop30ByInquiryIdAndIdLessThanOrderByIdDesc(Long inquiryId, Long cursorMessageId);
}
