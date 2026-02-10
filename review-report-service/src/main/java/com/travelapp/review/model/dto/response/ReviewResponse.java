package com.travelapp.review.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewResponse {

    private Long id;
    private Short rating;
    private String comment;
    private Boolean isHidden;
    private Integer likesCount;
    private Boolean likedByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long poiId;
    private Long userId;
    private String userName;
    private String userAvatar;
    private List<ReviewMediaResponse> media;
    private Integer totalMediaCount;
}