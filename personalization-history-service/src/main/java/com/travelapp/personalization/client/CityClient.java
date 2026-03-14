package com.travelapp.personalization.client;

import com.travelapp.personalization.model.dto.external.CityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "city-service",
        url = "${services.city.base-url}"
)
public interface CityClient {

    @GetMapping("/v1/{id}")
    CityDto getCityById(@PathVariable("id") Long cityId);
}