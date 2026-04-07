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
        return repository.findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.ACTIVE)
                .map(CityGraphVersion::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Для города " + cityId + " не подготовлен граф дорог"));
    }
}
