package com.travelapp.review.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponse {

    private Long totalReviews;
    private Double averageRating;
    private Long recentReviews24h;
    private Long pendingReports;
    private Long hiddenReviews;
}