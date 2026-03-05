package com.travelapp.city.controller;

import com.travelapp.city.model.dto.response.CityResponseDto;
import com.travelapp.city.security.SecurityUtils;
import com.travelapp.city.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User City", description = "User-specific city endpoints")
public class UserCityController {

    private final CityService cityService;

    @GetMapping("/home-city")
    @Operation(summary = "Get my home city")
    public ResponseEntity<CityResponseDto> getMyHomeCity() {
        Long homeCityId = SecurityUtils.getHomeCityIdOrNull();
        if (homeCityId == null) {
            return ResponseEntity.noContent().build(); // у пользователя не задан home_city_id
        }
        return ResponseEntity.ok(cityService.getCityById(homeCityId));
    }
}