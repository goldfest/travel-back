package com.travelapp.poi.model.imports.twogis;

import lombok.Data;

@Data
public class TwoGisRawHourDto {

    private Short dayOfWeek;
    private String openTime;
    private String closeTime;
    private Boolean aroundTheClock = false;
}
