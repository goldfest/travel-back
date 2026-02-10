package com.travelapp.review.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotNull(message = "Report type is required")
    private String reportType;

    @Size(max = 1000, message = "Comment must be less than 1000 characters")
    private String comment;

    private String photoUrl;

    private Long reviewId;

    private Long poiId;
}