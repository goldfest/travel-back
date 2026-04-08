package com.travelapp.route.service;

import com.travelapp.route.model.dto.response.CityGraphStatusResponse;

public interface CityGraphDownloadService {
    CityGraphStatusResponse getStatus(Long cityId);
    CityGraphStatusResponse download(Long cityId);
}
