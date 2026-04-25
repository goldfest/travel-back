package com.travelapp.review.mapper;

import com.travelapp.review.model.dto.response.ReportMediaResponse;
import com.travelapp.review.model.entity.ReportMedia;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ReportMediaMapper {

    public ReportMediaResponse toResponse(ReportMedia media) {
        if (media == null) {
            return null;
        }

        return ReportMediaResponse.builder()
                .id(media.getId())
                .reportId(media.getReport() != null ? media.getReport().getId() : null)
                .url(media.getFileUrl())
                .mediaType("PHOTO")
                .originalFilename(media.getOriginalFilename())
                .contentType(media.getContentType())
                .fileSize(media.getFileSize())
                .userId(media.getUploadedByUserId())
                .createdAt(media.getCreatedAt())
                .build();
    }

    public List<ReportMediaResponse> toResponseList(List<ReportMedia> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) {
            return Collections.emptyList();
        }

        return mediaList.stream()
                .map(this::toResponse)
                .toList();
    }
}