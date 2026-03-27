package com.travelapp.route.model.dto.response;

import lombok.Data;

@Data
public class MapViewportDto {
    private Double minLat;
    private Double minLng;
    private Double maxLat;
    private Double maxLng;
    private Double centerLat;
    private Double centerLng;
}