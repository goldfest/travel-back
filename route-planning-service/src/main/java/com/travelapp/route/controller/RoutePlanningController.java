package com.travelapp.route.controller;

import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.service.RoutePlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/route-planning")
@RequiredArgsConstructor
@Tag(name = "Route Planning", description = "API для планирования маршрутов")
@Slf4j
public class RoutePlanningController {

    private final RoutePlanningService routePlanningService;

    @GetMapping("/nearest-toilet")
    @Operation(summary = "Найти ближайший туалет")
    public ResponseEntity<PoiResponse> findNearestToilet(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Широта текущего местоположения")
            @RequestParam double latitude,
            @Parameter(description = "Долгота текущего местоположения")
            @RequestParam double longitude,
            @Parameter(description = "Максимальное расстояние в метрах", example = "1000")
            @RequestParam(defaultValue = "1000") int maxDistance,
            @Parameter(description = "Требуется бесплатный туалет")
            @RequestParam(defaultValue = "false") boolean freeOnly,
            @Parameter(description = "Требуется круглосуточный туалет")
            @RequestParam(defaultValue = "false") boolean aroundTheClock) {

        log.info("Finding nearest toilet for user {} at ({}, {})", userId, latitude, longitude);

        PoiResponse toilet = routePlanningService.findNearestToilet(
                latitude, longitude, maxDistance, freeOnly, aroundTheClock);

        return toilet != null ?
                ResponseEntity.ok(toilet) :
                ResponseEntity.noContent().build();
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Получить предложения для маршрута")
    public ResponseEntity<List<PoiResponse>> getRouteSuggestions(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "ID города")
            @RequestParam Long cityId,
            @Parameter(description = "Тип объектов", example = "museum")
            @RequestParam(required = false) String poiType,
            @Parameter(description = "Максимальное количество предложений", example = "10")
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Минимальный рейтинг", example = "4.0")
            @RequestParam(defaultValue = "4.0") double minRating) {

        log.info("Getting route suggestions for user {} in city {}", userId, cityId);

        List<PoiResponse> suggestions = routePlanningService.getRouteSuggestions(
                userId, cityId, poiType, limit, minRating);

        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/generate/{cityId}")
    @Operation(summary = "Сгенерировать маршрут автоматически")
    public ResponseEntity<Object> generateRoute(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long cityId,
            @Parameter(description = "Количество дней", example = "3")
            @RequestParam(defaultValue = "3") int days,
            @Parameter(description = "Интересы (через запятую)", example = "history,culture,food")
            @RequestParam(required = false) String interests,
            @Parameter(description = "Бюджет (1-4)", example = "2")
            @RequestParam(defaultValue = "2") int budgetLevel,
            @Parameter(description = "Режим передвижения", example = "WALK")
            @RequestParam(defaultValue = "WALK") String transportMode) {

        log.info("Generating route for user {} in city {} for {} days", userId, cityId, days);

        Object generatedRoute = routePlanningService.generateRoute(
                userId, cityId, days, interests, budgetLevel, transportMode);

        return ResponseEntity.ok(generatedRoute);
    }

    @GetMapping("/estimate-time")
    @Operation(summary = "Оценить время маршрута")
    public ResponseEntity<Integer> estimateRouteTime(
            @RequestParam List<Double> latitudes,
            @RequestParam List<Double> longitudes,
            @Parameter(description = "Режим передвижения", example = "WALK")
            @RequestParam(defaultValue = "WALK") String transportMode) {

        if (latitudes.size() != longitudes.size() || latitudes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        int estimatedTime = routePlanningService.estimateRouteTime(
                latitudes, longitudes, transportMode);

        return ResponseEntity.ok(estimatedTime);
    }
}