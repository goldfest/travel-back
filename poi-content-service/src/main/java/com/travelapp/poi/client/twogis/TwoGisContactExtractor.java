package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class TwoGisContactExtractor {

    public String extractContactPhone(JsonNode item) {
        JsonNode contacts = item.path("contact_groups");

        if (contacts.isArray()) {
            for (JsonNode group : contacts) {
                JsonNode contactsArray = group.path("contacts");

                if (contactsArray.isArray()) {
                    for (JsonNode contact : contactsArray) {
                        String type = contact.path("type").asText("");

                        if ("phone".equalsIgnoreCase(type)) {
                            JsonNode value = contact.path("value");

                            if (!value.isMissingNode() && !value.isNull()) {
                                String phone = value.asText(null);

                                if (StringUtils.isNotBlank(phone)) {
                                    return phone;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public String extractSiteUrl(JsonNode item) {
        JsonNode siteUrl = item.path("site_url");

        if (!siteUrl.isMissingNode() && !siteUrl.isNull()) {
            String value = siteUrl.asText(null);

            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }
}
