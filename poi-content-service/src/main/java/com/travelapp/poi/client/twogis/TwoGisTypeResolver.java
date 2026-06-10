package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TwoGisTypeResolver {

    private final TwoGisRubricExtractor rubricExtractor;
    private final TwoGisPoiTypeDictionary typeDictionary;

    public String resolvePoiTypeCode(JsonNode item) {
        return typeDictionary.resolveType(buildClassificationText(item));
    }

    public String inferRequestedPoiType(String query) {
        return typeDictionary.inferRequestType(query);
    }

    public boolean matchesRequestedType(String requestedType, String resolvedType) {
        return typeDictionary.accepts(requestedType, resolvedType);
    }

    public boolean passesStrictTypeFilter(JsonNode item, String requestedType) {
        if (StringUtils.isBlank(requestedType)) {
            return true;
        }

        String resolvedType = resolvePoiTypeCode(item);
        return typeDictionary.accepts(requestedType, resolvedType);
    }

    private String buildClassificationText(JsonNode item) {
        List<String> rubrics = rubricExtractor.extractRubricNames(item);

        return String.join(" ",
                StringUtils.defaultString(item.path("name").asText("")),
                StringUtils.defaultString(item.path("subtitle").asText("")),
                StringUtils.defaultString(item.path("purpose_name").asText("")),
                StringUtils.defaultString(item.path("description").asText("")),
                String.join(" ", rubrics)
        );
    }
}
