package com.travelapp.review.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReportResponse {
    private Long id;
    private String reportType;
    private String comment;
    private String moderatorComment;
    private String status;
    private String photoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;

    private Long userId;
    private Long handledByUserId;
    private Long reviewId;
    private Long poiId;

    private String userName;
    private String userAvatarUrl;
    private String handledByUserName;
    private String handledByUserAvatarUrl;
}