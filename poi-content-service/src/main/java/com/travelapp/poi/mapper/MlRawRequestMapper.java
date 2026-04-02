package com.travelapp.poi.mapper;

import com.travelapp.poi.model.ml.request.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MlRawRequestMapper {

    public MlEnrichRawRequest buildRequest(
            Long cityId,
            String language,
            String poiTypeHint,
            String sourceCode,
            String sourceUrl,
            String externalId,
            String name,
            String description,
            String address,
            Double latitude,
            Double longitude,
            String phone,
            String siteUrl,
            Integer priceLevel,
            String poiTypeCode,
            Map<String, String> features,
            List<MlRawHourDto> hours,
            List<MlRawMediaDto> media
    ) {
        MlRawSourceDto source = new MlRawSourceDto();
        source.setSourceCode(sourceCode);
        source.setSourceUrl(sourceUrl);
        source.setExternalId(externalId);

        MlRawPoiDto rawPoi = new MlRawPoiDto();
        rawPoi.setName(name);
        rawPoi.setDescription(description);
        rawPoi.setAddress(address);
        rawPoi.setLatitude(latitude);
        rawPoi.setLongitude(longitude);
        rawPoi.setPhone(phone);
        rawPoi.setSiteUrl(siteUrl);
        rawPoi.setPriceLevel(priceLevel);
        rawPoi.setPoiTypeCode(poiTypeCode);
        rawPoi.setFeatures(features != null ? features : Map.of());
        rawPoi.setHours(hours != null ? hours : new ArrayList<>());
        rawPoi.setMedia(media != null ? media : new ArrayList<>());
        rawPoi.setSource(source);

        MlEnrichRawRequest request = new MlEnrichRawRequest();
        request.setCityId(cityId);
        request.setLanguage(language != null ? language : "ru");
        request.setPoiTypeHint(poiTypeHint);
        request.setRawPoi(rawPoi);

        return request;
    }
}
