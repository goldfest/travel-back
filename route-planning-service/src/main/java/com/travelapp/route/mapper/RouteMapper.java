package com.travelapp.route.mapper;

import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteUpdateRequest;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.model.entity.Route;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {RouteDayMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RouteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "routeDays", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "isOptimized", constant = "false")
    @Mapping(target = "distanceKm", ignore = true)
    @Mapping(target = "durationMin", ignore = true)
    @Mapping(target = "startPoint", ignore = true)
    @Mapping(target = "endPoint", ignore = true)
    Route toEntity(RouteCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "routeDays", ignore = true)
    @Mapping(target = "distanceKm", ignore = true)
    @Mapping(target = "durationMin", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Route route, RouteUpdateRequest request);

    @Mapping(target = "daysCount", expression = "java(route.getRouteDays() != null ? route.getRouteDays().size() : 0)")
    @Mapping(target = "totalPoints", expression = "java(calculateTotalPoints(route))")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "warnings", ignore = true)
    RouteResponse toResponse(Route route);

    List<RouteResponse> toResponseList(List<Route> routes);

    default Integer calculateTotalPoints(Route route) {
        if (route.getRouteDays() == null || route.getRouteDays().isEmpty()) {
            return 0;
        }
        return route.getRouteDays().stream()
                .mapToInt(day -> day.getRoutePoints() != null ? day.getRoutePoints().size() : 0)
                .sum();
    }
}
