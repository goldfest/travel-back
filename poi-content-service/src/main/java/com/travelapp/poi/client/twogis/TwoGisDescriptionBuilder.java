package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.travelapp.poi.client.twogis.TwoGisTextUtils.cleanHtmlToText;
import static com.travelapp.poi.client.twogis.TwoGisTextUtils.firstNonBlank;
import static com.travelapp.poi.client.twogis.TwoGisTextUtils.normalizeText;

@Component
@RequiredArgsConstructor
public class TwoGisDescriptionBuilder {

    private static final int MIN_EXTERNAL_DESCRIPTION_LENGTH = 80;
    private static final int MAX_SENTENCES = 4;

    private final TwoGisPoiTypeDictionary typeDictionary;

    private record PhraseRule(List<String> keywords, String phrase) {
        boolean matches(String text) {
            return containsAny(text, keywords);
        }
    }

    private record FeatureHighlightRule(List<String> keywords, Function<String, String> phraseFactory) {
        String build(Map.Entry<String, String> entry) {
            String key = normalizeLower(entry.getKey());
            String value = normalizeText(entry.getValue());

            if ("false".equalsIgnoreCase(value)) {
                return null;
            }

            if (!containsAny(key, keywords)) {
                return null;
            }

            return phraseFactory.apply(StringUtils.defaultString(value));
        }
    }

    private static final List<PhraseRule> NAME_PHRASE_RULES = List.of(
            new PhraseRule(List.of("дом-музей", "дом музей"), "Дом-музей, связанный с историей и культурным наследием города."),
            new PhraseRule(List.of("квартира-музей", "квартира музей"), "Квартира-музей, связанная с историей и культурным наследием города."),
            new PhraseRule(List.of("художественный музей"), "Художественный музей с экспозициями, посвящёнными искусству и культуре."),
            new PhraseRule(List.of("краеведческий музей"), "Краеведческий музей, посвящённый истории и культуре региона."),
            new PhraseRule(List.of("мемориальный музей"), "Мемориальный музей, посвящённый важным историческим событиям и личностям."),
            new PhraseRule(List.of("музей"), "Музей, посвящённый истории, культуре или памятным событиям."),
            new PhraseRule(List.of("парк культуры"), "Парк культуры и отдыха для прогулок и досуга."),
            new PhraseRule(List.of("парк"), "Парк для прогулок, отдыха и времяпрепровождения на свежем воздухе."),
            new PhraseRule(List.of("сквер"), "Сквер — благоустроенное городское пространство для прогулок и отдыха."),
            new PhraseRule(List.of("сад"), "Сад — зелёная зона для спокойного отдыха."),
            new PhraseRule(List.of("кофейн"), "Кофейня, где можно отдохнуть и заказать горячие напитки."),
            new PhraseRule(List.of("кафе"), "Кафе для отдыха, встреч и повседневного посещения."),
            new PhraseRule(List.of("ресторан"), "Ресторан для обедов, ужинов и встреч."),
            new PhraseRule(List.of("бар", "паб"), "Бар для отдыха и встреч в городской среде."),
            new PhraseRule(List.of("пекар"), "Пекарня с выпечкой и напитками."),
            new PhraseRule(List.of("отель", "гостиниц", "хостел"), "Гостиница для размещения и временного проживания гостей."),
            new PhraseRule(List.of("туалет", "wc", "уборная"), "Общественный туалет для посетителей города.")
    );

    private static final List<FeatureHighlightRule> FEATURE_HIGHLIGHT_RULES = List.of(
            new FeatureHighlightRule(List.of("чек"), value -> {
                String amount = extractNumber(value);
                return StringUtils.isNotBlank(amount) ? "средний чек — " + amount + " ₽" : null;
            }),
            new FeatureHighlightRule(List.of("кухня"), value -> {
                String cuisine = normalizeCuisinePhrase(value);
                return StringUtils.isNotBlank(cuisine) ? "представлена " + cuisine : null;
            }),
            new FeatureHighlightRule(List.of("завтрак"), value -> "подают завтраки"),
            new FeatureHighlightRule(List.of("wi-fi", "wifi"), value -> "есть Wi‑Fi"),
            new FeatureHighlightRule(List.of("доставка"), value -> "доступна доставка"),
            new FeatureHighlightRule(List.of("навынос"), value -> "можно заказать навынос"),
            new FeatureHighlightRule(List.of("с ноутбуком"), value -> "можно работать с ноутбуком"),
            new FeatureHighlightRule(List.of("летняя веранда", "веранда"), value -> "есть летняя веранда"),
            new FeatureHighlightRule(List.of("с собакой", "dog", "pet"), value -> "можно с собакой"),
            new FeatureHighlightRule(List.of("доступный вход", "доступная среда", "wheelchair"), value -> "есть условия для маломобильных посетителей"),
            new FeatureHighlightRule(List.of("детская площадка", "детские площадки"), value -> "есть детская площадка"),
            new FeatureHighlightRule(List.of("мест"), value -> {
                String amount = extractNumber(value);
                return StringUtils.isNotBlank(amount) ? "до " + amount + " мест" : null;
            })
    );

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

        LinkedHashSet<String> sentences = new LinkedHashSet<>();

        if (StringUtils.isNotBlank(cleanDescription) && cleanDescription.length() >= MIN_EXTERNAL_DESCRIPTION_LENGTH) {
            sentences.add(cleanDescription);
        }

        addIfPresent(sentences, buildBaseSentence(name, purposeName, subtitle, rubricNames, resolvedType));
        addIfPresent(sentences, buildHighlightsSentence(features));
        addIfPresent(sentences, buildAddressSentence(address));

        if (sentences.isEmpty()) {
            return StringUtils.isNotBlank(address)
                    ? "Объект находится по адресу: " + address + "."
                    : "Информация об объекте ограничена.";
        }

        return sentences.stream()
                .filter(StringUtils::isNotBlank)
                .map(this::normalizeSentence)
                .distinct()
                .limit(MAX_SENTENCES)
                .reduce((left, right) -> left + " " + right)
                .orElse("Информация об объекте ограничена.");
    }

    private String buildBaseSentence(
            String name,
            String purposeName,
            String subtitle,
            List<String> rubricNames,
            String resolvedType
    ) {
        String normalizedName = normalizeText(name);
        String categoryPhrase = firstNonBlank(
                phraseFromName(name),
                phraseFromRubrics(rubricNames),
                normalizePhrase(purposeName),
                normalizePhrase(subtitle),
                typeDictionary.displayPhrase(resolvedType)
        );

        if (StringUtils.isBlank(categoryPhrase)) {
            return null;
        }

        String category = categoryPhrase.replaceAll("\\.$", "").trim();

        if (StringUtils.isBlank(normalizedName)) {
            return category + ".";
        }

        if (StringUtils.containsIgnoreCase(category, normalizedName)) {
            return category + ".";
        }

        return normalizedName + " — " + lowerFirst(category) + ".";
    }

    private String buildHighlightsSentence(Map<String, String> features) {
        List<String> highlights = extractDescriptionHighlights(features);
        if (highlights.isEmpty()) {
            return null;
        }

        return capitalizeSentence("Особенности: " + String.join(", ", highlights) + ".");
    }

    private List<String> extractDescriptionHighlights(Map<String, String> features) {
        LinkedHashSet<String> result = new LinkedHashSet<>();

        if (features == null || features.isEmpty()) {
            return List.of();
        }

        for (Map.Entry<String, String> entry : features.entrySet()) {
            for (FeatureHighlightRule rule : FEATURE_HIGHLIGHT_RULES) {
                addHighlightIfPresent(result, rule.build(entry));
                if (result.size() >= 5) {
                    return result.stream().toList();
                }
            }
        }

        return result.stream().toList();
    }

    private String phraseFromName(String name) {
        String normalized = normalizeLower(name);
        return NAME_PHRASE_RULES.stream()
                .filter(rule -> rule.matches(normalized))
                .map(PhraseRule::phrase)
                .findFirst()
                .orElse(null);
    }

    private String phraseFromRubrics(List<String> rubricNames) {
        if (rubricNames == null || rubricNames.isEmpty()) {
            return null;
        }

        String rubricsText = normalizeLower(String.join(" ", rubricNames));

        String dictionaryPhrase = typeDictionary.typeRules().stream()
                .filter(rule -> rule.matches(rubricsText))
                .map(rule -> typeDictionary.displayPhrase(rule.code()))
                .findFirst()
                .orElse(null);

        if (StringUtils.isNotBlank(dictionaryPhrase)) {
            return dictionaryPhrase;
        }

        List<String> fallback = rubricNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(2)
                .toList();

        return fallback.isEmpty() ? null : capitalizeSentence(String.join(", ", fallback) + ".");
    }

    private void addHighlightIfPresent(Set<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }

    private void addIfPresent(Set<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }

    private String buildAddressSentence(String address) {
        return StringUtils.isNotBlank(address) ? "Расположен по адресу: " + address + "." : null;
    }

    private static String normalizeCuisinePhrase(String cuisine) {
        String normalized = normalizeText(cuisine);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        String lower = normalizeLower(normalized).replaceAll("\\.$", "").trim();

        if (lower.endsWith(" кухня")) {
            String adjective = lower.substring(0, lower.length() - " кухня".length()).trim();
            return toGenitiveCuisineAdjective(adjective) + " кухня";
        }

        if (!lower.contains("кух")) {
            return lower + " кухня";
        }

        return lower;
    }

    private static String toGenitiveCuisineAdjective(String adjective) {
        if (StringUtils.isBlank(adjective)) {
            return adjective;
        }

        String word = adjective.trim().toLowerCase(Locale.ROOT);

        if (word.endsWith("ская")) {
            return word.substring(0, word.length() - 4) + "ская";
        }
        if (word.endsWith("ческая")) {
            return word.substring(0, word.length() - 6) + "ческая";
        }

        return word;
    }

    private static String extractNumber(String... values) {
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

    private String normalizeSentence(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        normalized = normalized.replaceAll("\\s+([.,!?;:])", "$1");
        normalized = normalized.replaceAll("([.!?]){2,}", "$1");

        if (!normalized.endsWith(".") && !normalized.endsWith("!") && !normalized.endsWith("?")) {
            normalized += ".";
        }

        return capitalizeSentence(normalized);
    }

    private String normalizePhrase(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        normalized = normalized.replaceAll("\\.$", "");
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1) + ".";
    }

    private static String capitalizeSentence(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String lowerFirst(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return value;
        }
        return Character.toLowerCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String normalizeLower(String value) {
        return StringUtils.defaultString(value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, List<String> values) {
        if (StringUtils.isBlank(text)) {
            return false;
        }

        String normalizedText = normalizeLower(text);
        return values.stream()
                .filter(StringUtils::isNotBlank)
                .map(TwoGisDescriptionBuilder::normalizeLower)
                .anyMatch(normalizedText::contains);
    }
}
