package com.travelapp.route.controller;

import com.travelapp.route.model.dto.response.RouteMapResponse;
import com.travelapp.route.security.SecurityUtils;
import com.travelapp.route.service.RouteMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/routes")
@RequiredArgsConstructor
public class RouteMapController {

    private final RouteMapService routeMapService;

    @GetMapping("/{id}/map")
    public ResponseEntity<RouteMapResponse> getRouteMap(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeMapService.getRouteMap(userId, id));
    }
}