package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.model.imports.twogis.TwoGisRawHourDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class TwoGisHoursExtractor {

    public List<TwoGisRawHourDto> extractHours(JsonNode item) {
        List<TwoGisRawHourDto> result = new ArrayList<>();
        JsonNode schedule = item.path("schedule");

        if (schedule.isMissingNode() || schedule.isNull() || !schedule.isObject()) {
            return result;
        }

        Map<String, Short> dayMap = Map.of(
                "Sun", (short) 0,
                "Mon", (short) 1,
                "Tue", (short) 2,
                "Wed", (short) 3,
                "Thu", (short) 4,
                "Fri", (short) 5,
                "Sat", (short) 6
        );

        Iterator<Map.Entry<String, JsonNode>> fields = schedule.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String dayCode = entry.getKey();
            JsonNode dayNode = entry.getValue();

            Short dayOfWeek = dayMap.get(dayCode);
            if (dayOfWeek == null || dayNode == null || dayNode.isNull()) {
                continue;
            }

            JsonNode workingHours = dayNode.path("working_hours");
            if (!workingHours.isArray() || workingHours.isEmpty()) {
                continue;
            }

            for (JsonNode interval : workingHours) {
                String from = interval.path("from").asText(null);
                String to = interval.path("to").asText(null);

                if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
                    continue;
                }

                TwoGisRawHourDto dto = new TwoGisRawHourDto();
                dto.setDayOfWeek(dayOfWeek);
                dto.setOpenTime(normalizeTime(from));
                dto.setCloseTime(normalizeTime(to));
                dto.setAroundTheClock(false);

                result.add(dto);
            }
        }

        return result;
    }

    private String normalizeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if ("24:00".equals(normalized)) {
            return "23:59";
        }

        return normalized;
    }
}
