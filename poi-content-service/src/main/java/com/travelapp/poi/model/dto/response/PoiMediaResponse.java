package com.travelapp.poi.model.dto.response;

import com.travelapp.poi.model.entity.PoiMedia;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PoiMediaResponse {
    private Long id;
    private Long poiId;
    private String url;
    private PoiMedia.MediaType mediaType;
    private PoiMedia.SourceType sourceType;
    private PoiMedia.ModerationStatus moderationStatus;
    private Integer displayOrder;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String rejectionReason;
    private Long userId;
    private Long moderatedBy;
    private LocalDateTime moderatedAt;
    private LocalDateTime createdAt;
}
