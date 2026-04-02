package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPoiDraft {

    private String name;
    private String slug;
    private List<String> tags = new ArrayList<>();
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;

    @JsonProperty("site_url")
    private String siteUrl;

    @JsonProperty("price_level")
    private Integer priceLevel;

    @JsonProperty("city_id")
    private Long cityId;

    @JsonProperty("poi_type_code")
    private String poiTypeCode;

    private Map<String, String> features = Map.of();
    private List<MlPoiHourDraft> hours = new ArrayList<>();
    private List<MlPoiMediaDraft> media = new ArrayList<>();
    private List<MlPoiSourceDraft> sources = new ArrayList<>();
}
