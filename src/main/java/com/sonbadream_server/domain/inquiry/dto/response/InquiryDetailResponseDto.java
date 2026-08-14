package com.sonbadream_server.domain.inquiry.dto.response;

import com.sonbadream_server.domain.inquiry.entity.InquiryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InquiryDetailResponseDto {
    private Long inquiryId;
    private InquiryStatus status;
    private Long requesterUserId;
    private Long targetUserId;
}
