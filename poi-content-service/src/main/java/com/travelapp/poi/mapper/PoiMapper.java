package com.travelapp.poi.mapper;

import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PoiMapper {

    @Mapping(target = "poiType", source = "poiType")
    @Mapping(target = "features", expression = "java(mapFeatures(poi.getFeatures()))")
    @Mapping(target = "hours", expression = "java(mapHours(poi.getHours()))")
    @Mapping(target = "media", expression = "java(mapMedia(poi.getMedia()))")
    @Mapping(target = "sources", expression = "java(mapSources(poi.getSources()))")
    @Mapping(target = "distanceKm", ignore = true)  // Будет заполняться отдельно
    @Mapping(target = "isOpenNow", ignore = true)   // Будет заполняться в сервисе
    @Mapping(target = "currentStatus", ignore = true) // Будет заполняться в сервисе
    PoiResponse toResponse(Poi poi);

    default Map<String, String> mapFeatures(Set<PoiFeature> features) {
        if (features == null) return null;
        return features.stream()
                .collect(Collectors.toMap(
                        PoiFeature::getKey,
                        PoiFeature::getValue
                ));
    }

    default List<PoiResponse.PoiHoursResponse> mapHours(Set<PoiHours> hours) {
        if (hours == null) return null;
        return hours.stream()
                .map(this::toHoursResponse)
                .collect(Collectors.toList());
    }

    default List<PoiResponse.PoiMediaResponse> mapMedia(Set<PoiMedia> media) {
        if (media == null) return null;
        return media.stream()
                .map(this::toMediaResponse)
                .collect(Collectors.toList());
    }

    default List<PoiResponse.PoiSourceResponse> mapSources(Set<PoiSource> sources) {
        if (sources == null) return null;
        return sources.stream()
                .map(this::toSourceResponse)
                .collect(Collectors.toList());
    }

    @Mapping(target = "isToday", ignore = true) // Будет заполняться отдельно
    PoiResponse.PoiHoursResponse toHoursResponse(PoiHours hours);

    PoiResponse.PoiMediaResponse toMediaResponse(PoiMedia media);

    PoiResponse.PoiSourceResponse toSourceResponse(PoiSource source);

    default PoiResponse.PoiTypeResponse toTypeResponse(PoiType poiType) {
        if (poiType == null) return null;

        PoiResponse.PoiTypeResponse response = new PoiResponse.PoiTypeResponse();
        response.setId(poiType.getId());
        response.setCode(poiType.getCode());
        response.setName(poiType.getName());
        response.setIcon(poiType.getIcon());
        return response;
    }
}