package com.travelapp.review.mapper;

import com.travelapp.review.model.dto.response.ReviewMediaResponse;
import com.travelapp.review.model.entity.ReviewMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReviewMediaMapper {

    @Mapping(target = "thumbnailUrl", expression = "java(generateThumbnailUrl(media.getImageUrl()))")
    ReviewMediaResponse toResponse(ReviewMedia media);

    List<ReviewMediaResponse> toResponseList(List<ReviewMedia> mediaList);

    default String generateThumbnailUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        // Для локального файлового хранилища отдельный thumbnail пока не формируется.
        // Возвращаем тот же URL, чтобы фронт мог использовать одно поле.
        if (imageUrl.startsWith("/api/")) {
            return imageUrl;
        }
        return imageUrl.replace("/upload/", "/upload/w_200,h_200,c_fill/");
    }

    default ReviewMedia toEntity(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        return ReviewMedia.builder()
                .imageUrl(imageUrl)
                .sourceType(ReviewMedia.SourceType.USER_UPLOAD)
                .moderationStatus(ReviewMedia.ModerationStatus.APPROVED)
                .build();
    }
}
