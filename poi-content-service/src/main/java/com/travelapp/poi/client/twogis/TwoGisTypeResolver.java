package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class TwoGisTypeResolver {

    private final TwoGisRubricExtractor rubricExtractor;

    public String resolvePoiTypeCode(JsonNode item) {
        List<String> rubrics = rubricExtractor.extractRubricNames(item);

        String text = String.join(" ",
                StringUtils.defaultString(item.path("name").asText("")),
                StringUtils.defaultString(item.path("subtitle").asText("")),
                StringUtils.defaultString(item.path("purpose_name").asText("")),
                String.join(" ", rubrics)
        ).toLowerCase(Locale.ROOT);

        if (containsAny(text, "кафе", "кофейня", "coffee")) {
            return "cafe";
        }
        if (containsAny(text, "ресторан", "бар", "паб", "столовая", "пиццерия", "бургер")) {
            return "restaurant";
        }
        if (containsAny(text, "отель", "отели", "гостиница", "гостиницы", "хостел", "хостелы", "апартаменты")) {
            return "hotel";
        }
        if (containsAny(text, "парк", "сквер", "сад")) {
            return "park";
        }
        if (containsAny(text, "музей", "собор", "храм", "театр", "памятник", "достопримечательность", "галерея")) {
            return "landmark";
        }
        if (containsAny(text, "магазин", "shop")) {
            return "shop";
        }
        if (containsAny(text, "аптека")) {
            return "pharmacy";
        }
        if (containsAny(text, "больница", "клиника")) {
            return "hospital";
        }
        if (containsAny(text, "школа", "университет")) {
            return "school";
        }
        if (containsAny(text, "банкомат", "atm")) {
            return "atm";
        }

        return "landmark";
    }

    public String inferRequestedPoiType(String query) {
        String normalized = StringUtils.defaultString(query).toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "кафе", "кофейня", "кофейни")) {
            return "cafe";
        }
        if (containsAny(normalized, "ресторан", "рестораны", "бар", "паб", "пиццерия", "фастфуд")) {
            return "restaurant";
        }
        if (containsAny(normalized, "отель", "отели", "гостиница", "гостиницы", "хостел", "хостелы")) {
            return "hotel";
        }
        if (containsAny(normalized, "парк", "парки", "сквер", "сад")) {
            return "park";
        }
        if (containsAny(normalized, "музей", "музеи", "театр", "театры", "собор", "храм", "памятник", "достопримечательность")) {
            return "landmark";
        }

        return null;
    }

    public boolean matchesRequestedType(String requestedType, String resolvedType) {
        return requestedType == null || StringUtils.equalsIgnoreCase(requestedType, resolvedType);
    }

    public boolean passesStrictTypeFilter(JsonNode item, String requestedType) {
        if (StringUtils.isBlank(requestedType)) {
            return true;
        }

        String text = buildClassificationText(item);

        return switch (requestedType.toLowerCase(Locale.ROOT)) {
            case "park" -> containsAny(text, "парк", "сквер", "сад", "park");
            case "landmark" -> containsAny(text,
                    "музей", "театр", "собор", "храм", "памятник", "галерея",
                    "достопримечательность", "музей-заповедник", "выставочный зал");
            case "cafe" -> containsAny(text, "кафе", "кофейня", "coffee");
            case "restaurant" -> containsAny(text, "ресторан", "бар", "паб", "пиццерия", "бургер", "столовая");
            case "hotel" -> containsAny(text, "отель", "гостиница", "хостел", "апартаменты");
            default -> true;
        };
    }

    private String buildClassificationText(JsonNode item) {
        List<String> rubrics = rubricExtractor.extractRubricNames(item);

        return String.join(" ",
                StringUtils.defaultString(item.path("name").asText("")),
                StringUtils.defaultString(item.path("subtitle").asText("")),
                StringUtils.defaultString(item.path("purpose_name").asText("")),
                String.join(" ", rubrics)
        ).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }

        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }

        return false;
    }
}
