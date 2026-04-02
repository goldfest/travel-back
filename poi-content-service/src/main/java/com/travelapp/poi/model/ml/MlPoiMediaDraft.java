package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPoiMediaDraft {

    private String url;

    @JsonProperty("media_type")
    private String mediaType;
}