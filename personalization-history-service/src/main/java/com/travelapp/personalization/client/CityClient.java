package com.travelapp.personalization.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "city-service",
        url = "${services.city.base-url}"
)
public interface CityClient {

    @GetMapping("/{id}")
    Object getCityById(@PathVariable("id") Long cityId);
}