package com.travelapp.route.service.impl;

import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.repository.CityGraphVersionRepository;
import com.travelapp.route.service.GraphVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GraphVersionServiceImpl implements GraphVersionService {

    private final CityGraphVersionRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Long getRequiredActiveVersionId(Long cityId) {
        return getActiveVersionOrThrow(cityId).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public CityGraphVersion getActiveVersionOrThrow(Long cityId) {
        return repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Для города " + cityId + " не подготовлен граф дорог"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveVersion(Long cityId) {
        return repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.ACTIVE).isPresent();
    }
}