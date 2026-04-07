package com.travelapp.poi.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InternalPoiLiteResponse {
    Long id;
    Long cityId;
    String name;
    Double latitude;
    Double longitude;
    Boolean isVerified;
    Boolean isClosed;
}
