package com.travelapp.poi.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.poi.exception.PoiTypeNotFoundException;
import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.entity.PoiMedia;
import com.travelapp.poi.model.entity.PoiType;
import com.travelapp.poi.model.ml.*;
import com.travelapp.poi.repository.PoiTypeRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MlPoiMapper {

    private final PoiTypeRepository poiTypeRepository;
    private final ObjectMapper objectMapper;

    public PoiCreateRequest toPoiCreateRequest(MlEnrichResponse enrichResponse) {
        if (enrichResponse == null || enrichResponse.getPoiDraft() == null) {
            throw new IllegalArgumentException("ML enrich response or poiDraft is null");
        }

        MlPoiDraft draft = enrichResponse.getPoiDraft();

        PoiType poiType = poiTypeRepository.findByCode(draft.getPoiTypeCode())
                .orElseThrow(() -> new PoiTypeNotFoundException("Code: " + draft.getPoiTypeCode()));

        PoiCreateRequest request = new PoiCreateRequest();
        request.setName(draft.getName());
        request.setSlug(draft.getSlug());
        request.setCityId(draft.getCityId());
        request.setPoiTypeId(poiType.getId());

        if (draft.getLatitude() != null) {
            request.setLatitude(BigDecimal.valueOf(draft.getLatitude()));
        }

        if (draft.getLongitude() != null) {
            request.setLongitude(BigDecimal.valueOf(draft.getLongitude()));
        }

        request.setAddress(draft.getAddress());
        request.setDescription(draft.getDescription());
        request.setPhone(normalizePhone(draft.getPhone()));
        request.setSiteUrl(draft.getSiteUrl());

        if (draft.getPriceLevel() != null) {
            request.setPriceLevel(draft.getPriceLevel().shortValue());
        }

        if (draft.getTags() != null) {
            request.setTags(objectMapper.valueToTree(draft.getTags()));
        }

        request.setFeatures(draft.getFeatures());

        request.setHours(mapHours(draft.getHours()));
        request.setMedia(mapMedia(draft.getMedia()));
        request.setSources(mapSources(draft.getSources()));

        return request;
    }

    private List<PoiCreateRequest.HoursRequest> mapHours(List<MlPoiHourDraft> hours) {
        List<PoiCreateRequest.HoursRequest> result = new ArrayList<>();
        if (hours == null) return result;

        for (MlPoiHourDraft hour : hours) {
            boolean aroundTheClock = Boolean.TRUE.equals(hour.getAroundTheClock());
            boolean hasAnyTime = StringUtils.isNotBlank(hour.getOpenTime()) || StringUtils.isNotBlank(hour.getCloseTime());

            if (hour.getDayOfWeek() == null) {
                continue;
            }

            if (!aroundTheClock && !hasAnyTime) {
                continue;
            }

            PoiCreateRequest.HoursRequest item = new PoiCreateRequest.HoursRequest();
            item.setDayOfWeek(hour.getDayOfWeek());
            item.setOpenTime(parseLocalTime(hour.getOpenTime()));
            item.setCloseTime(parseLocalTime(hour.getCloseTime()));
            item.setAroundTheClock(aroundTheClock);
            result.add(item);
        }

        return result;
    }

    private List<PoiCreateRequest.MediaRequest> mapMedia(List<MlPoiMediaDraft> media) {
        List<PoiCreateRequest.MediaRequest> result = new ArrayList<>();
        if (media == null) return result;

        for (MlPoiMediaDraft mediaItem : media) {
            if (mediaItem == null || StringUtils.isBlank(mediaItem.getUrl())) {
                continue;
            }

            PoiCreateRequest.MediaRequest item = new PoiCreateRequest.MediaRequest();
            item.setUrl(mediaItem.getUrl().trim());
            item.setMediaType(resolveMediaType(mediaItem.getMediaType()));
            result.add(item);
        }

        return result;
    }

    private List<PoiCreateRequest.SourceRequest> mapSources(List<MlPoiSourceDraft> sources) {
        List<PoiCreateRequest.SourceRequest> result = new ArrayList<>();
        if (sources == null) return result;

        for (MlPoiSourceDraft source : sources) {
            if (source == null
                    || StringUtils.isBlank(source.getSourceCode())
                    || StringUtils.isBlank(source.getSourceUrl())) {
                continue;
            }

            PoiCreateRequest.SourceRequest item = new PoiCreateRequest.SourceRequest();
            item.setSourceCode(source.getSourceCode().trim());
            item.setSourceUrl(source.getSourceUrl().trim());

            if (source.getConfidenceScore() != null) {
                item.setConfidenceScore(BigDecimal.valueOf(source.getConfidenceScore()));
            }

            result.add(item);
        }

        return result;
    }

    private LocalTime parseLocalTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalTime.parse(value);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[^+\\d]", "");
    }

    private PoiMedia.MediaType resolveMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return PoiMedia.MediaType.IMAGE;
        }

        try {
            return PoiMedia.MediaType.valueOf(mediaType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PoiMedia.MediaType.IMAGE;
        }
    }
}
