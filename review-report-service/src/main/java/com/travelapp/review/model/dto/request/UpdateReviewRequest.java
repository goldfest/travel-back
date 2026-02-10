package com.travelapp.review.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReviewRequest {

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Short rating;

    @Size(max = 1000, message = "Comment must be less than 1000 characters")
    private String comment;

    private Boolean isHidden;

    private List<String> imageUrlsToAdd;

    private List<Long> imageIdsToRemove;
}