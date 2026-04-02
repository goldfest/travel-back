package com.travelapp.poi.model.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlPoiHourDraft {

    @JsonProperty("day_of_week")
    private Short dayOfWeek;

    @JsonProperty("open_time")
    private String openTime;

    @JsonProperty("close_time")
    private String closeTime;

    @JsonProperty("around_the_clock")
    private Boolean aroundTheClock;
}
