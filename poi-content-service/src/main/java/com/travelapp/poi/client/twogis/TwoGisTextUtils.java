package com.travelapp.poi.client.twogis;

import org.apache.commons.lang3.StringUtils;

public final class TwoGisTextUtils {

    private TwoGisTextUtils() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    public static String cleanHtmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<p[^>]*>", "")
                .replaceAll("(?i)<a[^>]*>", "")
                .replaceAll("(?i)</a>", "")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");

        text = normalizeText(text);
        return StringUtils.isBlank(text) ? null : text;
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }

}
