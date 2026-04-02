package com.travelapp.poi.model.ml.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MlEnrichRawRequest {

    @JsonProperty("city_id")
    private Long cityId;

    private String language = "ru";

    @JsonProperty("poi_type_hint")
    private String poiTypeHint;

    @JsonProperty("raw_poi")
    private MlRawPoiDto rawPoi;
}
