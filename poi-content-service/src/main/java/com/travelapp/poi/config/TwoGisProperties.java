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
    private Integer pageSize = 10;
    private Integer maxPages = 20;

    private Integer gridRadiusKm = 12;     // радиус вокруг центра города
    private Integer cellStepKm = 4;        // шаг между точками сетки
    private Integer searchRadiusMeters = 2500;
}