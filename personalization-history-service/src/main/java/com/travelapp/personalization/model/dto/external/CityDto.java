package com.travelapp.personalization.model.dto.external;

import lombok.Data;

@Data
public class CityDto {
    private Long id;
    private String name;
    private String slug;
    private String country;
}