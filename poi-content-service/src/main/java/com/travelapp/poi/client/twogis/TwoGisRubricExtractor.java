package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TwoGisRubricExtractor {

    public List<String> extractRubricNames(JsonNode item) {
        List<String> result = new ArrayList<>();
        JsonNode rubrics = item.path("rubrics");

        if (!rubrics.isArray()) {
            return result;
        }

        for (JsonNode rubric : rubrics) {
            String name = rubric.path("name").asText(null);
            if (StringUtils.isNotBlank(name)) {
                result.add(name.trim());
            }
        }

        return result;
    }
}
