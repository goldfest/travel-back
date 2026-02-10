package com.travelapp.poi.service;

import com.travelapp.poi.model.dto.request.PoiTypeRequest;
import com.travelapp.poi.model.dto.response.PoiTypeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PoiTypeService {

    PoiTypeResponse createPoiType(PoiTypeRequest request, Long userId);

    PoiTypeResponse updatePoiType(Long id, PoiTypeRequest request, Long userId);

    void deletePoiType(Long id, Long userId);

    PoiTypeResponse getPoiTypeById(Long id);

    PoiTypeResponse getPoiTypeByCode(String code);

    List<PoiTypeResponse> getAllPoiTypes();

    Page<PoiTypeResponse> getPoiTypes(Pageable pageable);

    List<PoiTypeResponse> getPoiTypesByCodes(List<String> codes);
}