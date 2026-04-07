package com.travelapp.route.client;

import com.travelapp.route.model.dto.response.PoiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "poi-service", url = "${services.poi.base-url}")
public interface PoiClient {

    @GetMapping("/pois/{id}")
    PoiResponse getPoiById(@PathVariable("id") Long id);

    @GetMapping("/pois/nearby")
    List<PoiResponse> searchNearby(
            @RequestParam("cityId") Long cityId,
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(value = "radiusKm", defaultValue = "5") int radiusKm,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    );

    @GetMapping("/pois/batch")
    List<PoiResponse> getPoisBatch(@RequestParam("ids") List<Long> ids);

    @GetMapping("/pois/search/by-city-and-type")
    List<PoiResponse> searchByCityAndType(
            @RequestParam("cityId") Long cityId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    );
}