package com.travelapp.review.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoiReviewStatsResponse {

    private Long poiId;
    private Long totalReviews;
    private Double averageRating;
    private Long visibleReviews;
    private Long oneStarCount;
    private Long twoStarsCount;
    private Long threeStarsCount;
    private Long fourStarsCount;
    private Long fiveStarsCount;
}