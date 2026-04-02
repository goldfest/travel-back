package com.travelapp.poi.model.ml.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MlRawSourceDto {

    @JsonProperty("source_code")
    private String sourceCode;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("external_id")
    private String externalId;
}