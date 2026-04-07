package com.travelapp.graphimport.model.dto;

import lombok.Data;

@Data
public class InternalPoiLiteResponse {
    private Long id;
    private Long cityId;
    private String name;
    private Double latitude;
    private Double longitude;
    private Boolean isVerified;
    private Boolean isClosed;
}
