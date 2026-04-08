package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlQualityInfo {

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("quality_score")
    private Double qualityScore;

    @JsonProperty("toxicity_detected")
    private Boolean toxicityDetected;

    @JsonProperty("stop_words_detected")
    private List<String> stopWordsDetected = new ArrayList<>();

    @JsonProperty("errors")
    private List<String> errors = new ArrayList<>();

    @JsonProperty("warnings")
    private List<String> warnings = new ArrayList<>();
}