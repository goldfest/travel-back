package com.travelapp.poi.service;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.entity.Poi;

import java.util.Optional;

public interface PoiDuplicateDetectionService {

    Optional<Poi> findDuplicate(PoiCreateRequest request);
}
