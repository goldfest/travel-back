package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlQualityInfo {

    private Double confidenceScore;
    private Double qualityScore;
    private Boolean toxicityDetected;
    private List<String> stopWordsDetected = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
