package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPoiSourceDraft {

    @JsonProperty("source_code")
    private String sourceCode;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("confidence_score")
    private Double confidenceScore;
}