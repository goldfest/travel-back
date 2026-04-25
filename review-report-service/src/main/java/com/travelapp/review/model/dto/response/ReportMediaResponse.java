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
public class ReportMediaResponse {

    private Long id;
    private Long reportId;
    private String url;
    private String mediaType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Long userId;
    private LocalDateTime createdAt;
}
