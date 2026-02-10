package com.travelapp.review.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewMediaResponse {

    private Long id;
    private String imageUrl;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
}