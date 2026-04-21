package com.sonbadrim_server.domain.search.dto.response;

import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SearchEngineerListResponseDto {
    private List<EngineerSummaryResponseDto> items;
    private boolean hasNext;
}
