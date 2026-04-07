package com.travelapp.personalization.model.dto.request;

import com.travelapp.personalization.validation.ValidJson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryRequest {

    private String queryText;
    @ValidJson
    private String filtersJson;
    private Long cityId;
    private Long presetFilterId;
}