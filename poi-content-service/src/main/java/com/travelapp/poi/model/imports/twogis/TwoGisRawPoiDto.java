package com.travelapp.poi.model.imports.twogis;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TwoGisRawPoiDto {

    private String externalId;
    private String name;
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String siteUrl;
    private Integer priceLevel;
    private String poiTypeCode;
    private Map<String, String> features = Map.of();
    private List<TwoGisRawHourDto> hours = new ArrayList<>();
    private List<TwoGisRawMediaDto> media = new ArrayList<>();
    private String sourceUrl;
    private String purposeName;
    private List<String> rubricNames = new ArrayList<>();
    private Boolean hasPhotos;
    private String staticMapUrl;
}