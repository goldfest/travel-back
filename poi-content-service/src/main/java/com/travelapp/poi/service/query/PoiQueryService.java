package com.travelapp.poi.service.query;

import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface PoiQueryService {
    PoiResponse getPoiById(Long id);
    PoiResponse getPoiBySlug(String slug);

    Page<PoiResponse> searchPois(PoiSearchRequest request);

    List<PoiResponse> getNearbyPois(Long cityId, BigDecimal userLat, BigDecimal userLng,
                                    Integer radiusKm, Integer limit);

    Page<PoiResponse> getPoisByCity(Long cityId, Pageable pageable);
    Page<PoiResponse> getPoisByType(Long poiTypeId, Pageable pageable);

    Page<PoiResponse> getUnverifiedPois(Pageable pageable);

    long getPoiCountByCity(Long cityId);
    long getPoiCountByType(Long poiTypeId);
}