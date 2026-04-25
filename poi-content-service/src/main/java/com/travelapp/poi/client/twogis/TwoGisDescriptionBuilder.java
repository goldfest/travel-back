package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.travelapp.poi.client.twogis.TwoGisTextUtils.cleanHtmlToText;
import static com.travelapp.poi.client.twogis.TwoGisTextUtils.firstNonBlank;
import static com.travelapp.poi.client.twogis.TwoGisTextUtils.normalizeText;

@Component
public class TwoGisDescriptionBuilder {

    public String buildDescription(
            JsonNode item,
            String name,
            String purposeName,
            List<String> rubricNames,
            String address,
            Map<String, String> features,
            String resolvedType
    ) {
        String cleanDescription = cleanHtmlToText(item.path("description").asText(null));
        String subtitle = normalizeText(item.path("subtitle").asText(null));

        if (StringUtils.isNotBlank(cleanDescription) && cleanDescription.length() >= 50) {
            return cleanDescription;
        }

        List<String> sentences = new ArrayList<>();

        String parkPhrase = "park".equalsIgnoreCase(resolvedType)
                ? buildParkPhrase(features)
                : null;

        String baseType = firstNonBlank(
                normalizePhrase(purposeName),
                normalizePhrase(subtitle),
                buildCuisineTypePhrase(name, rubricNames, features),
                buildMuseumPhrase(name),
                parkPhrase,
                buildTypePhrase(rubricNames),
                buildTypePhraseFromName(name)
        );

        List<String> highlights = extractDescriptionHighlights(features);

        if (StringUtils.isNotBlank(baseType)) {
            sentences.add(baseType);
        }

        if (!highlights.isEmpty()) {
            String secondSentence = String.join(", ", highlights);
            if (!secondSentence.endsWith(".")) {
                secondSentence += ".";
            }
            sentences.add(capitalizeSentence(secondSentence));
        }

        if (sentences.isEmpty()) {
            if (StringUtils.isNotBlank(address)) {
                return "Объект находится по адресу: " + address + ".";
            }
            return "Информация об объекте ограничена.";
        }

        return String.join(" ", sentences);
    }

    private List<String> extractDescriptionHighlights(Map<String, String> features) {
        LinkedHashSet<String> result = new LinkedHashSet<>();

        if (features == null || features.isEmpty()) {
            return List.of();
        }

        addHighlightIfPresent(result, findAverageBill(features));
        addHighlightIfPresent(result, findBreakfast(features));
        addHighlightIfPresent(result, findWifi(features));
        addHighlightIfPresent(result, findDelivery(features));
        addHighlightIfPresent(result, findTakeaway(features));
        addHighlightIfPresent(result, findLaptop(features));
        addHighlightIfPresent(result, findVeranda(features));
        addHighlightIfPresent(result, findDogFriendly(features));
        addHighlightIfPresent(result, findSeats(features));

        return result.stream().limit(4).toList();
    }

    private void addHighlightIfPresent(Set<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }

    private String findAverageBill(Map<String, String> features) {
        for (Map.Entry<String, String> entry : features.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String value = StringUtils.defaultString(entry.getValue()).trim();

            if (key.contains("чек")) {
                String amount = extractNumber(value, key);
                if (StringUtils.isNotBlank(amount)) {
                    return "средний чек — " + amount + " ₽";
                }
            }
        }
        return null;
    }

    private String findBreakfast(Map<String, String> features) {
        return hasFeature(features, "завтрак") ? "подают завтраки" : null;
    }

    private String findWifi(Map<String, String> features) {
        return hasFeature(features, "wi-fi", "wifi") ? "есть Wi-Fi" : null;
    }

    private String findDelivery(Map<String, String> features) {
        return hasFeature(features, "доставка") ? "есть доставка" : null;
    }

    private String findTakeaway(Map<String, String> features) {
        return hasFeature(features, "навынос") ? "можно заказать навынос" : null;
    }

    private String findLaptop(Map<String, String> features) {
        return hasFeature(features, "с ноутбуком") ? "можно с ноутбуком" : null;
    }

    private String findVeranda(Map<String, String> features) {
        return hasFeature(features, "летняя веранда", "веранда") ? "есть летняя веранда" : null;
    }

    private String findDogFriendly(Map<String, String> features) {
        return hasFeature(features, "с собакой") ? "можно с собакой" : null;
    }

    private String findSeats(Map<String, String> features) {
        for (Map.Entry<String, String> entry : features.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String value = StringUtils.defaultString(entry.getValue()).trim();

            if (key.contains("мест")) {
                String amount = extractNumber(value, key);
                if (StringUtils.isNotBlank(amount)) {
                    return "до " + amount + " мест";
                }
            }
        }
        return null;
    }

    private boolean hasFeature(Map<String, String> features, String... keywords) {
        if (features == null || features.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, String> entry : features.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String value = StringUtils.defaultString(entry.getValue()).trim();

            if ("false".equalsIgnoreCase(value)) {
                continue;
            }

            for (String keyword : keywords) {
                if (key.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }

        return false;
    }

    private String buildCuisineTypePhrase(String name, List<String> rubricNames, Map<String, String> features) {
        String cuisine = findCuisine(features);
        if (StringUtils.isBlank(cuisine)) {
            return null;
        }

        String baseType = firstNonBlank(
                buildTypePhrase(rubricNames),
                buildTypePhraseFromName(name)
        );

        if (StringUtils.isBlank(baseType)) {
            return null;
        }

        String base = baseType.replaceAll("\\.$", "").trim();
        String cuisinePhrase = normalizeCuisinePhrase(cuisine);

        if (StringUtils.isBlank(cuisinePhrase)) {
            return null;
        }

        return base + " " + cuisinePhrase + ".";
    }

    private String normalizeCuisinePhrase(String cuisine) {
        String normalized = normalizeText(cuisine);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        String lower = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("\\.$", "")
                .trim();

        if (lower.endsWith(" кухня")) {
            String adjective = lower.substring(0, lower.length() - " кухня".length()).trim();
            String inflected = toGenitiveCuisineAdjective(adjective);
            return inflected + " кухни";
        }

        return lower;
    }

    private String toGenitiveCuisineAdjective(String adjective) {
        if (StringUtils.isBlank(adjective)) {
            return adjective;
        }

        String word = adjective.trim().toLowerCase(Locale.ROOT);

        if (word.endsWith("ая")) {
            return word.substring(0, word.length() - 2) + "ой";
        }
        if (word.endsWith("яя")) {
            return word.substring(0, word.length() - 2) + "ей";
        }
        if (word.endsWith("ская")) {
            return word.substring(0, word.length() - 4) + "ской";
        }
        if (word.endsWith("ческая")) {
            return word.substring(0, word.length() - 6) + "ческой";
        }

        return word;
    }

    private String findCuisine(Map<String, String> features) {
        if (features == null || features.isEmpty()) {
            return null;
        }

        for (String key : features.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("кухня")) {
                String[] parts = key.split("\\.");
                return parts.length > 1 ? parts[parts.length - 1] : key;
            }
        }

        return null;
    }

    private String buildTypePhraseFromName(String name) {
        String normalized = StringUtils.defaultString(name).trim().toLowerCase(Locale.ROOT);

        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        if (normalized.contains("дом-музей") || normalized.contains("дом музей")) {
            return "Дом-музей.";
        }
        if (normalized.contains("художественный музей")) {
            return "Художественный музей.";
        }
        if (normalized.contains("краеведческий музей")) {
            return "Краеведческий музей.";
        }
        if (normalized.contains("музей")) {
            return "Музей.";
        }

        if (normalized.contains("парк культуры")) {
            return "Парк культуры и отдыха.";
        }
        if (normalized.contains("парк")) {
            return "Парк.";
        }
        if (normalized.contains("сквер")) {
            return "Сквер.";
        }
        if (normalized.contains("сад")) {
            return "Сад.";
        }

        if (normalized.contains("кофейн")) return "Кофейня.";
        if (normalized.contains("кафе")) return "Кафе.";
        if (normalized.contains("ресторан")) return "Ресторан.";
        if (normalized.contains("пекар")) return "Пекарня.";
        if (normalized.contains("бар")) return "Бар.";
        if (normalized.contains("отель") || normalized.contains("гостиниц")) return "Гостиница.";

        return null;
    }

    private String buildParkPhrase(Map<String, String> features) {
        if (features == null || features.isEmpty()) {
            return null;
        }

        boolean hasPlaygrounds = hasFeature(features, "детские площадки", "детская площадка");
        boolean hasAccessible = hasFeature(features, "доступный вход", "доступная среда");

        if (hasPlaygrounds) {
            return "Парк с детскими площадками.";
        }
        if (hasAccessible) {
            return "Парк с доступной средой.";
        }

        return null;
    }

    private String buildMuseumPhrase(String name) {
        String normalized = StringUtils.defaultString(name).trim().toLowerCase(Locale.ROOT);

        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        if (normalized.contains("дом-музей") || normalized.contains("дом музей")) {
            return "Дом-музей.";
        }
        if (normalized.contains("художественный музей")) {
            return "Художественный музей.";
        }
        if (normalized.contains("краеведческий музей")) {
            return "Краеведческий музей.";
        }
        if (normalized.contains("мемориальный музей")) {
            return "Мемориальный музей.";
        }
        if (normalized.contains("музей")) {
            return "Музей.";
        }

        return null;
    }

    private String extractNumber(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (StringUtils.isBlank(value)) {
                continue;
            }

            String digits = value.replaceAll("[^\\d]", "");
            if (StringUtils.isNotBlank(digits)) {
                return digits;
            }
        }

        return null;
    }

    private String buildTypePhrase(List<String> rubricNames) {
        if (rubricNames == null || rubricNames.isEmpty()) {
            return null;
        }

        List<String> normalized = rubricNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();

        boolean hasCoffeeShop = normalized.stream().anyMatch(v -> v.contains("кофейн"));
        boolean hasCafe = normalized.stream().anyMatch(v -> v.equals("кафе") || v.contains("кафе"));
        boolean hasRestaurant = normalized.stream().anyMatch(v -> v.contains("ресторан"));
        boolean hasConfectionery = normalized.stream().anyMatch(v -> v.contains("кондитер"));
        boolean hasBakery = normalized.stream().anyMatch(v -> v.contains("пекар"));
        boolean hasBar = normalized.stream().anyMatch(v -> v.contains("бар"));
        boolean hasFastFood = normalized.stream().anyMatch(v -> v.contains("фастфуд") || v.contains("быстрое питание"));
        boolean hasHotel = normalized.stream().anyMatch(v -> v.contains("отел") || v.contains("гостиниц") || v.contains("хостел"));
        boolean hasMuseum = normalized.stream().anyMatch(v -> v.contains("музе"));
        boolean hasPark = normalized.stream().anyMatch(v -> v.contains("парк") || v.contains("сквер") || v.contains("сад"));

        if (hasCoffeeShop && hasConfectionery) {
            return "Кофейня и кондитерская.";
        }
        if (hasCafe && hasConfectionery) {
            return "Кафе и кондитерская.";
        }
        if (hasRestaurant) {
            return "Ресторан.";
        }
        if (hasCoffeeShop) {
            return "Кофейня.";
        }
        if (hasCafe) {
            return "Кафе.";
        }
        if (hasBar) {
            return "Бар.";
        }
        if (hasFastFood) {
            return "Заведение быстрого питания.";
        }
        if (hasBakery) {
            return "Пекарня.";
        }
        if (hasHotel) {
            return "Гостиница.";
        }
        if (hasMuseum) {
            return "Музей.";
        }
        if (hasPark) {
            return "Парк.";
        }

        List<String> fallback = rubricNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(2)
                .toList();

        if (fallback.isEmpty()) {
            return null;
        }

        return capitalizeSentence(String.join(", ", fallback) + ".");
    }

    private String normalizePhrase(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        normalized = normalized.replaceAll("\\.$", "");
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1) + ".";
    }

    private String capitalizeSentence(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

}
