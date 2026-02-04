package com.travelapp.route.client;

import com.travelapp.route.model.dto.response.PoiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@FeignClient(name = "poi-service", url = "${app.poi-service.url}")
public interface PoiClient {

    @GetMapping("/{id}")
    Optional<PoiResponse> getPoiById(@PathVariable("id") Long id);

    @GetMapping("/search/nearby")
    List<PoiResponse> searchNearby(
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(value = "radius", defaultValue = "1000") int radius,
            @RequestParam(value = "type", required = false) String type);

    @GetMapping("/search")
    List<PoiResponse> searchByCityAndType(
            @RequestParam("cityId") Long cityId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", defaultValue = "50") int limit);

    @GetMapping("/batch")
    List<PoiResponse> getPoisBatch(@RequestParam("ids") List<Long> ids);
}