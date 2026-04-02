package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlEnrichResponse {

    @JsonProperty("poi_draft")
    private MlPoiDraft poiDraft;

    private MlQualityInfo quality;

    @JsonProperty("status_recommendation")
    private MlStatusRecommendation statusRecommendation;
}