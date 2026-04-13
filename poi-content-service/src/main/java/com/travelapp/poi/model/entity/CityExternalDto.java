package com.travelapp.poi.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CityExternalDto {
    private Long id;
    private String name;
    private String slug;
    private BigDecimal centerLat;
    private BigDecimal centerLng;
}
