package com.travelapp.poi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "services.two-gis")
public class TwoGisProperties {
    private String baseUrl;
    private String apiKey;
}