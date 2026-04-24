package com.travelapp.poi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.media")
@Getter
@Setter
public class PoiMediaStorageProperties {

    private String uploadDir = "uploads/poi-media";

    private String publicUrlPrefix = "/api/poi/media/poi";

    private long maxFileSizeBytes = 5 * 1024 * 1024;
}
