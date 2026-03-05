package com.travelapp.poi.service.impl;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.service.PoiService;
import com.travelapp.poi.service.command.PoiCommandService;
import com.travelapp.poi.service.query.PoiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PoiServiceImpl implements PoiService {

    private final PoiCommandService commandService;
    private final PoiQueryService queryService;

    @Override
    public PoiResponse createPoi(PoiCreateRequest request, Long userId) {
        return commandService.createPoi(request, userId);
    }

    @Override
    public PoiResponse getPoiById(Long id) {
        return queryService.getPoiById(id);
    }

    @Override
    public PoiResponse getPoiBySlug(String slug) {
        return queryService.getPoiBySlug(slug);
    }

    @Override
    public PoiResponse updatePoi(Long id, PoiUpdateRequest request, Long userId) {
        return commandService.updatePoi(id, request, userId);
    }

    @Override
    public void deletePoi(Long id, Long userId) {
        commandService.deletePoi(id, userId);
    }

    @Override
    public void verifyPoi(Long id, Long adminId) {
        commandService.verifyPoi(id, adminId);
    }

    @Override
    public void unverifyPoi(Long id, Long adminId) {
        commandService.unverifyPoi(id, adminId);
    }

    @Override
    public Page<PoiResponse> searchPois(PoiSearchRequest request) {
        return queryService.searchPois(request);
    }

    @Override
    public List<PoiResponse> getNearbyPois(Long cityId, BigDecimal userLat, BigDecimal userLng, Integer radiusKm, Integer limit) {
        return queryService.getNearbyPois(cityId, userLat, userLng, radiusKm, limit);
    }

    @Override
    public Page<PoiResponse> getPoisByCity(Long cityId, Pageable pageable) {
        return queryService.getPoisByCity(cityId, pageable);
    }

    @Override
    public Page<PoiResponse> getPoisByType(Long poiTypeId, Pageable pageable) {
        return queryService.getPoisByType(poiTypeId, pageable);
    }

    @Override
    public Page<PoiResponse> getUnverifiedPois(Pageable pageable) {
        return queryService.getUnverifiedPois(pageable);
    }

    @Override
    public long getPoiCountByCity(Long cityId) {
        return queryService.getPoiCountByCity(cityId);
    }

    @Override
    public long getPoiCountByType(Long poiTypeId) {
        return queryService.getPoiCountByType(poiTypeId);
    }

    @Override
    public void updateRating(Long poiId, BigDecimal newRating) {
        commandService.updateRating(poiId, newRating);
    }
}