package com.travelapp.review.service;

import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import com.travelapp.review.model.dto.response.PoiReviewStatsResponse;
import com.travelapp.review.model.dto.response.ReviewResponse;
import com.travelapp.review.model.dto.response.ReviewSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(Long userId, CreateReviewRequest request);

    ReviewResponse createReviewWithMedia(Long userId, Long poiId, Short rating, String comment, List<MultipartFile> files);

    ReviewResponse getReviewById(Long id, Long currentUserId);

    Page<ReviewResponse> getReviewsByPoiId(Long poiId, Long currentUserId, Pageable pageable);

    Page<ReviewResponse> getReviewsByUserId(Long userId, Pageable pageable);

    Page<ReviewResponse> getPendingReviews(Pageable pageable);

    ReviewResponse updateReview(Long id, Long userId, UpdateReviewRequest request);

    ReviewResponse addMediaToReview(Long id, Long userId, List<MultipartFile> files);

    void deleteReview(Long id, Long userId);

    void hideReview(Long id, Long moderatorId);

    void unhideReview(Long id, Long moderatorId);

    ReviewResponse approveReview(Long id, Long moderatorId, String moderationComment);

    ReviewResponse rejectReview(Long id, Long moderatorId, String moderationComment);

    ReviewResponse toggleLike(Long reviewId, Long userId);

    PoiReviewStatsResponse getPoiReviewStats(Long poiId);

    ReviewSummaryResponse getReviewSummary(Long moderatorId);

    boolean hasUserReviewedPoi(Long userId, Long poiId);

    ReviewResponse getReviewByPoiAndUser(Long poiId, Long userId);

}