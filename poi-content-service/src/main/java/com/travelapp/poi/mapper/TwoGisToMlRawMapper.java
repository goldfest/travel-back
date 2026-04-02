package com.travelapp.poi.mapper;

import com.travelapp.poi.model.imports.twogis.TwoGisRawHourDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawMediaDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import com.travelapp.poi.model.ml.request.MlEnrichRawRequest;
import com.travelapp.poi.model.ml.request.MlRawHourDto;
import com.travelapp.poi.model.ml.request.MlRawMediaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TwoGisToMlRawMapper {

    private final MlRawRequestMapper mlRawRequestMapper;

    public MlEnrichRawRequest toMlRequest(TwoGisRawPoiDto rawPoi, Long cityId) {
        List<MlRawHourDto> hours = new ArrayList<>();
        if (rawPoi.getHours() != null) {
            for (TwoGisRawHourDto hour : rawPoi.getHours()) {
                MlRawHourDto dto = new MlRawHourDto();
                dto.setDayOfWeek(hour.getDayOfWeek());
                dto.setOpenTime(hour.getOpenTime());
                dto.setCloseTime(hour.getCloseTime());
                dto.setAroundTheClock(Boolean.TRUE.equals(hour.getAroundTheClock()));
                hours.add(dto);
            }
        }

        List<MlRawMediaDto> media = new ArrayList<>();
        if (rawPoi.getMedia() != null) {
            for (TwoGisRawMediaDto item : rawPoi.getMedia()) {
                MlRawMediaDto dto = new MlRawMediaDto();
                dto.setUrl(item.getUrl());
                dto.setMediaType(item.getMediaType());
                media.add(dto);
            }
        }

        return mlRawRequestMapper.buildRequest(
                cityId,
                "ru",
                rawPoi.getPoiTypeCode(),
                "TWO_GIS",
                rawPoi.getSourceUrl(),
                rawPoi.getExternalId(),
                rawPoi.getName(),
                rawPoi.getDescription(),
                rawPoi.getAddress(),
                rawPoi.getLatitude(),
                rawPoi.getLongitude(),
                rawPoi.getPhone(),
                rawPoi.getSiteUrl(),
                rawPoi.getPriceLevel(),
                rawPoi.getPoiTypeCode(),
                rawPoi.getFeatures(),
                hours,
                media
        );
    }
}