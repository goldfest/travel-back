package com.travelapp.poi.mapper;

import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PoiMapper {

    @Mapping(target = "poiType", source = "poiType")
    @Mapping(target = "features", source = "features", qualifiedByName = "mapFeatures")
    @Mapping(target = "hours", source = "hours", qualifiedByName = "mapHours")
    @Mapping(target = "media", source = "media", qualifiedByName = "mapMedia")
    @Mapping(target = "sources", source = "sources", qualifiedByName = "mapSources")
    PoiResponse toResponse(Poi poi);

    @Named("mapFeatures")
    default Map<String, String> mapFeatures(List<PoiFeature> features) {
        if (features == null) return null;
        return features.stream()
                .collect(Collectors.toMap(
                        PoiFeature::getKey,
                        PoiFeature::getValue
                ));
    }

    @Named("mapHours")
    default List<PoiResponse.PoiHoursResponse> mapHours(List<PoiHours> hours) {
        if (hours == null) return null;
        return hours.stream()
                .map(this::toHoursResponse)
                .collect(Collectors.toList());
    }

    @Named("mapMedia")
    default List<PoiResponse.PoiMediaResponse> mapMedia(List<PoiMedia> media) {
        if (media == null) return null;
        return media.stream()
                .map(this::toMediaResponse)
                .collect(Collectors.toList());
    }

    @Named("mapSources")
    default List<PoiResponse.PoiSourceResponse> mapSources(List<PoiSource> sources) {
        if (sources == null) return null;
        return sources.stream()
                .map(this::toSourceResponse)
                .collect(Collectors.toList());
    }

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