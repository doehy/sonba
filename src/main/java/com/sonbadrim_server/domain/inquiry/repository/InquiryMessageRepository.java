package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.entity.InquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {


}
