package com.travelapp.route.mapper;

import com.travelapp.route.model.dto.response.RoutePointResponse;
import com.travelapp.route.model.entity.RoutePoint;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoutePointMapper {

    @Mapping(target = "estimatedArrivalTime", ignore = true)
    @Mapping(target = "estimatedDepartureTime", ignore = true)
    RoutePointResponse toResponse(RoutePoint point);

    List<RoutePointResponse> toResponseList(List<RoutePoint> points);

    @AfterMapping
    default void calculateTimes(@MappingTarget RoutePointResponse response, RoutePoint point) {
        // Здесь будет логика расчета времени прибытия/отбытия
        // Пока оставляем пустым
    }
}