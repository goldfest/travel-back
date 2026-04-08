package com.travelapp.route.controller;

import com.travelapp.route.model.dto.response.CityGraphStatusResponse;
import com.travelapp.route.service.CityGraphDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/graphs")
@RequiredArgsConstructor
public class GraphController {

    private final CityGraphDownloadService cityGraphDownloadService;

    @GetMapping("/cities/{cityId}/status")
    public ResponseEntity<CityGraphStatusResponse> getCityGraphStatus(@PathVariable Long cityId) {
        return ResponseEntity.ok(cityGraphDownloadService.getStatus(cityId));
    }

    @PostMapping("/cities/{cityId}/download")
    public ResponseEntity<CityGraphStatusResponse> downloadCityGraph(@PathVariable Long cityId) {
        return ResponseEntity.accepted().body(cityGraphDownloadService.download(cityId));
    }
}
