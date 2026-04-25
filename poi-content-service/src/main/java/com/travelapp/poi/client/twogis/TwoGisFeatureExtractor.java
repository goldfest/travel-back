package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.travelapp.poi.client.twogis.TwoGisTextUtils.firstNonBlank;
import static com.travelapp.poi.client.twogis.TwoGisTextUtils.normalizeText;

@Component
public class TwoGisFeatureExtractor {

    public Map<String, String> extractFeatures(JsonNode item) {
        Map<String, String> result = new LinkedHashMap<>();

        JsonNode attributeGroups = item.path("attribute_groups");
        if (!attributeGroups.isArray()) {
            return result;
        }

        for (JsonNode group : attributeGroups) {
            String groupName = normalizeText(group.path("name").asText(null));
            JsonNode attributes = group.path("attributes");

            if (!attributes.isArray()) {
                continue;
            }

            for (JsonNode attribute : attributes) {
                String attributeName = normalizeText(attribute.path("name").asText(null));
                String attributeText = extractAttributeText(attribute);

                if (StringUtils.isBlank(groupName)
                        && StringUtils.isBlank(attributeName)
                        && StringUtils.isBlank(attributeText)) {
                    continue;
                }

                putRawFeature(result, groupName, attributeName, attributeText);
            }
        }

        return result;
    }

    private void putRawFeature(Map<String, String> target,
                               String groupName,
                               String attributeName,
                               String attributeText) {
        String normalizedGroup = normalizeText(groupName);
        String normalizedName = normalizeText(attributeName);
        String normalizedText = normalizeText(attributeText);

        if (StringUtils.isBlank(normalizedGroup)
                && StringUtils.isBlank(normalizedName)
                && StringUtils.isBlank(normalizedText)) {
            return;
        }

        String key;
        String value;

        if (StringUtils.isNotBlank(normalizedGroup) && StringUtils.isNotBlank(normalizedName)) {
            key = normalizedGroup + "." + normalizedName;
            value = StringUtils.isNotBlank(normalizedText) ? normalizedText : "true";
        } else if (StringUtils.isNotBlank(normalizedName)) {
            key = normalizedName;
            value = StringUtils.isNotBlank(normalizedText) ? normalizedText : "true";
        } else if (StringUtils.isNotBlank(normalizedGroup) && StringUtils.isNotBlank(normalizedText)) {
            key = normalizedGroup + "." + normalizedText;
            value = "true";
        } else if (StringUtils.isNotBlank(normalizedText)) {
            key = normalizedText;
            value = "true";
        } else {
            key = normalizedGroup;
            value = "true";
        }

        target.put(key, value);
    }

    private String extractAttributeText(JsonNode attribute) {
        String directValue = firstNonBlank(
                normalizeText(attribute.path("text").asText(null)),
                normalizeText(attribute.path("value").asText(null))
        );

        if (StringUtils.isNotBlank(directValue)) {
            return directValue;
        }

        JsonNode values = attribute.path("values");
        if (values.isArray()) {
            List<String> parts = new ArrayList<>();

            for (JsonNode valueNode : values) {
                String value = firstNonBlank(
                        normalizeText(valueNode.path("name").asText(null)),
                        normalizeText(valueNode.path("text").asText(null)),
                        valueNode.isValueNode() ? normalizeText(valueNode.asText(null)) : null
                );

                if (StringUtils.isNotBlank(value)) {
                    parts.add(value);
                }
            }

            if (!parts.isEmpty()) {
                return String.join(", ", parts);
            }
        }

        return null;
    }
}
