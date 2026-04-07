package com.travelapp.graphimport.client;

import com.travelapp.graphimport.model.dto.InternalPoiLiteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "poi-internal-client", url = "${services.poi.base-url}")
public interface InternalPoiClient {

    @GetMapping("/cities/{cityId}/pois")
    List<InternalPoiLiteResponse> getCityPois(
            @PathVariable("cityId") Long cityId,
            @RequestParam(value = "onlyVerified", defaultValue = "true") boolean onlyVerified,
            @RequestParam(value = "onlyActive", defaultValue = "true") boolean onlyActive
    );
}
