package com.travelapp.poi.model.ml.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MlImportFromSourceRequest {

    @JsonProperty("source_code")
    private String sourceCode;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("city_id")
    private Long cityId;

    private String language = "ru";

    @JsonProperty("poi_type_hint")
    private String poiTypeHint;
}
