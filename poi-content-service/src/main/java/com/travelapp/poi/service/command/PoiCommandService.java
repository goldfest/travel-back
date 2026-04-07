package com.travelapp.poi.service.command;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PoiCommandService {
    PoiResponse createPoi(PoiCreateRequest request, Long userId);
    PoiResponse updatePoi(Long id, PoiUpdateRequest request, Long userId);

    void deletePoi(Long id, Long userId);

    void verifyPoi(Long id, Long adminId);
    void unverifyPoi(Long id, Long adminId);
    void verifyPoiInternal(Long id);

    PoiResponse updatePoiFromImport(Long id, PoiCreateRequest request, Long userId);
}