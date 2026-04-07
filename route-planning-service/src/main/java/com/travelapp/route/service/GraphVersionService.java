package com.travelapp.route.service;

import com.travelapp.route.model.entity.CityGraphVersion;

public interface GraphVersionService {
    Long getRequiredActiveVersionId(Long cityId);
    CityGraphVersion getActiveVersionOrThrow(Long cityId);
}