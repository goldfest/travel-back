package com.travelapp.personalization.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryRequest {

    private String queryText;
    private String filtersJson;
    private Long cityId;
    private Long presetFilterId;
}