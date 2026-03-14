package com.travelapp.personalization.client;

import com.travelapp.personalization.model.dto.external.PoiDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "poi-service",
        url = "${services.poi.base-url}"
)
public interface PoiClient {

    @GetMapping("/pois/{id}")
    PoiDto getPoiById(@PathVariable("id") Long poiId);
}