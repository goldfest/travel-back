package com.travelapp.review.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.media")
public class ReviewMediaStorageProperties {

    private String uploadDir = "uploads/review-media";

    private String publicUrlPrefix = "/api/reviews/media";

    private long maxFileSizeBytes = 5 * 1024 * 1024;
}

