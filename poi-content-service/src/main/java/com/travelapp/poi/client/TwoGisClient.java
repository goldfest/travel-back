package com.travelapp.poi.client;

import com.travelapp.poi.model.imports.twogis.TwoGisRawHourDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawMediaDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TwoGisClient {

    public List<TwoGisRawPoiDto> search(String query, Long cityId) {
        log.info("Searching 2GIS raw data for query='{}', cityId={}", query, cityId);

        TwoGisRawMediaDto media = new TwoGisRawMediaDto();
        media.setUrl("https://example.com/media/pushkin-1.jpg");
        media.setMediaType("IMAGE");

        TwoGisRawHourDto mon = new TwoGisRawHourDto();
        mon.setDayOfWeek((short) 1);
        mon.setOpenTime("10:00");
        mon.setCloseTime("22:00");
        mon.setAroundTheClock(false);

        TwoGisRawPoiDto poi = new TwoGisRawPoiDto();
        poi.setExternalId("2gis-pushkin-001");
        poi.setName("Ресторан Пушкин");
        poi.setDescription(
                "Известный ресторан русской кухни в центре города. " +
                        "Популярен среди туристов благодаря интерьеру и высокому уровню сервиса."
        );
        poi.setAddress("Москва, Тверской бульвар, 26А");
        poi.setLatitude(55.76495);
        poi.setLongitude(37.60442);
        poi.setPhone("+7-495-000-00-01");
        poi.setSiteUrl(query);
        poi.setPriceLevel(4);
        poi.setPoiTypeCode("restaurant");
        poi.setFeatures(Map.of(
                "parking", "false",
                "wifi", "true"
        ));
        poi.setHours(List.of(mon));
        poi.setMedia(List.of(media));
        poi.setSourceUrl(query);

        return List.of(poi);
    }
}
