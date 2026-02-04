package com.travelapp.route.mapper;

import com.travelapp.route.model.dto.response.RouteDayResponse;
import com.travelapp.route.model.entity.RouteDay;
import org.mapstruct.*;

import java.time.LocalTime;
import java.util.List;

@Mapper(componentModel = "spring",
        uses = {RoutePointMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RouteDayMapper {

    @Mapping(target = "pointsCount", expression = "java(day.getRoutePoints() != null ? day.getRoutePoints().size() : 0)")
    @Mapping(target = "startTime", source = "plannedStart", qualifiedByName = "extractTime")
    @Mapping(target = "endTime", source = "plannedEnd", qualifiedByName = "extractTime")
    @Mapping(target = "estimatedDurationHours", expression = "java(day.getEstimatedDurationHours())")
    RouteDayResponse toResponse(RouteDay day);

    List<RouteDayResponse> toResponseList(List<RouteDay> days);

    @Named("extractTime")
    default LocalTime extractTime(java.time.LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalTime() : null;
    }
}