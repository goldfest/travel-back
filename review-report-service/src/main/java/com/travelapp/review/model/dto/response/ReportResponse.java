package com.travelapp.review.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportResponse {

    private Long id;
    private String reportType;
    private String comment;
    private String status;
    private String photoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private Long userId;
    private Long handledByUserId;
    private String handledByUserName;
    private Long reviewId;
    private Long poiId;
    private String targetTitle; // Название отзыва или POI
    private String targetType; // "review" или "poi"
}