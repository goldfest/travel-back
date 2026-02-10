package com.travelapp.poi.service;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PoiService {

    PoiResponse createPoi(PoiCreateRequest request, Long userId);

    PoiResponse getPoiById(Long id);

    PoiResponse getPoiBySlug(String slug);

    PoiResponse updatePoi(Long id, PoiUpdateRequest request, Long userId);

    void deletePoi(Long id, Long userId);

    void verifyPoi(Long id, Long adminId);

    void unverifyPoi(Long id, Long adminId);

    Page<PoiResponse> searchPois(PoiSearchRequest request);

    List<PoiResponse> getNearbyPois(Long cityId, BigDecimal userLat, BigDecimal userLng, Integer radiusKm, Integer limit);

    Page<PoiResponse> getPoisByCity(Long cityId, Pageable pageable);

    Page<PoiResponse> getPoisByType(Long poiTypeId, Pageable pageable);

    Page<PoiResponse> getUnverifiedPois(Pageable pageable);

    long getPoiCountByCity(Long cityId);

    long getPoiCountByType(Long poiTypeId);

    void updateRating(Long poiId, BigDecimal newRating);
}