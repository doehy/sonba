package com.sonbadream_server.domain.inquiry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class InquiryMessagePageResponseDto {
    private List<InquiryDetailMessageResponseDto> items;
    private boolean hasNext;
    private Long nextCursor;
}
