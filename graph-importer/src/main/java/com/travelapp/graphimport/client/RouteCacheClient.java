package com.travelapp.graphimport.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "route-cache-client", url = "${services.route.base-url:http://route-service:8087/api/routes}")
public interface RouteCacheClient {

    @PostMapping("/internal/graph-cache/cities/{cityId}/evict")
    void evictCityGraphCache(@PathVariable("cityId") Long cityId);
}
