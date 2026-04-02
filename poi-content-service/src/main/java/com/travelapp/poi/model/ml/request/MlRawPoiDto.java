package com.travelapp.poi.model.ml.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MlRawPoiDto {

    private String name;
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;

    @JsonProperty("site_url")
    private String siteUrl;

    @JsonProperty("price_level")
    private Integer priceLevel;

    @JsonProperty("poi_type_code")
    private String poiTypeCode;

    private Map<String, String> features = Map.of();
    private List<MlRawHourDto> hours = new ArrayList<>();
    private List<MlRawMediaDto> media = new ArrayList<>();
    private MlRawSourceDto source;
}
