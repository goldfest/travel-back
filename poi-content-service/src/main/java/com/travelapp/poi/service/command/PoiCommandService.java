package com.travelapp.poi.service.command;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;

import java.math.BigDecimal;

public interface PoiCommandService {
    PoiResponse createPoi(PoiCreateRequest request, Long userId);
    PoiResponse updatePoi(Long id, PoiUpdateRequest request, Long userId);

    void deletePoi(Long id, Long userId);

    void verifyPoi(Long id, Long adminId);
    void unverifyPoi(Long id, Long adminId);

    void updateRating(Long poiId, BigDecimal newRating);
}