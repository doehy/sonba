package com.sonbadrim_server.domain.inquiry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class InquiryListResponseDto {
    private List<InquiryListItemResponseDto> items;
    private boolean hasNext;
}
