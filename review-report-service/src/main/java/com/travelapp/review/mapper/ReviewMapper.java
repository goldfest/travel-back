package com.travelapp.review.mapper;

import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import com.travelapp.review.model.dto.response.ReviewResponse;
import com.travelapp.review.model.entity.Review;
import com.travelapp.review.model.entity.ReviewMedia;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {ReviewMediaMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ReviewMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "media", ignore = true)
    @Mapping(target = "likes", ignore = true)
    @Mapping(target = "likesCount", constant = "0")
    @Mapping(target = "isHidden", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Review toEntity(CreateReviewRequest request);

    @Mapping(target = "media", source = "media")
    @Mapping(target = "totalMediaCount", expression = "java(review.getMedia() != null ? review.getMedia().size() : 0)")
    ReviewResponse toResponse(Review review);

    List<ReviewResponse> toResponseList(List<Review> reviews);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "media", ignore = true)
    @Mapping(target = "likes", ignore = true)
    @Mapping(target = "likesCount", ignore = true)
    @Mapping(target = "poiId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Review review, UpdateReviewRequest request);

    default ReviewResponse toResponseWithUserInfo(Review review, String userName, String userAvatar, Boolean likedByCurrentUser) {
        ReviewResponse response = toResponse(review);
        response.setUserName(userName);
        response.setUserAvatar(userAvatar);
        response.setLikedByCurrentUser(likedByCurrentUser);
        return response;
    }
}