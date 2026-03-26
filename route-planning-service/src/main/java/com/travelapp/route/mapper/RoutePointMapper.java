package com.travelapp.route.mapper;

import com.travelapp.route.model.dto.response.RoutePointResponse;
import com.travelapp.route.model.entity.RoutePoint;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoutePointMapper {

    @Mapping(target = "routeDayId", source = "routeDay.id")
    @Mapping(target = "estimatedVisitMinutes", source = "estimatedVisitMinutes")
    @Mapping(target = "plannedArrivalAt", source = "plannedArrivalAt")
    @Mapping(target = "plannedDepartureAt", source = "plannedDepartureAt")
    RoutePointResponse toResponse(RoutePoint point);

    List<RoutePointResponse> toResponseList(List<RoutePoint> points);
}
