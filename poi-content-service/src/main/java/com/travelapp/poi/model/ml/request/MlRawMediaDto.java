package com.travelapp.poi.model.ml.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MlRawMediaDto {

    private String url;

    @JsonProperty("media_type")
    private String mediaType;
}