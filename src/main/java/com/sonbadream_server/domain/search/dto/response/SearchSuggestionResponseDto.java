package com.sonbadream_server.domain.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SearchSuggestionResponseDto {
    private List<String> items;
}
