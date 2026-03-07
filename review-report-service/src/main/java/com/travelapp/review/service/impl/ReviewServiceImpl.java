package com.travelapp.review.service.impl;

import com.travelapp.review.client.PoiClient;
import com.travelapp.review.exception.ResourceNotFoundException;
import com.travelapp.review.mapper.ReviewMapper;
import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import com.travelapp.review.model.dto.response.PoiReviewStatsResponse;
import com.travelapp.review.model.dto.response.ReviewResponse;
import com.travelapp.review.model.dto.response.ReviewSummaryResponse;
import com.travelapp.review.model.entity.Review;
import com.travelapp.review.model.entity.ReviewLike;
import com.travelapp.review.repository.ReviewLikeRepository;
import com.travelapp.review.repository.ReviewRepository;
import com.travelapp.review.security.dto.AuthUser;
import com.travelapp.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewMapper reviewMapper;
    private final PoiClient poiClient;
    private final AuthUserResolverService authUserResolverService;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviewStats", key = "#request.poiId"),
            @CacheEvict(value = "poiReviews", allEntries = true)
    })
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        boolean poiExists = poiClient.checkPoiExists(request.getPoiId());
        if (!poiExists) {
            throw new ResourceNotFoundException("POI not found with id: " + request.getPoiId());
        }

        if (reviewRepository.existsByPoiIdAndUserId(request.getPoiId(), userId)) {
            throw new IllegalArgumentException("User has already reviewed this POI");
        }

        Review review = reviewMapper.toEntity(request);
        review.setUserId(userId);

        Review savedReview = reviewRepository.save(review);
        updatePoiRatingStats(request.getPoiId());

        AuthUser user = authUserResolverService.getUserInfoSafe(userId);
        String userName = user != null && user.getUsername() != null ? user.getUsername() : "User_" + userId;
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviewMapper.toResponseWithUserInfo(savedReview, userName, userAvatar, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long id, Long currentUserId) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        boolean likedByCurrentUser = currentUserId != null
                && reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, id);

        AuthUser user = authUserResolverService.getUserInfoSafe(review.getUserId());
        String userName = user != null && user.getUsername() != null ? user.getUsername() : "User_" + review.getUserId();
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedByCurrentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByPoiId(Long poiId, Long currentUserId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByPoiIdAndIsHiddenFalse(poiId, pageable);

        return reviews.map(review -> {
            boolean likedByCurrentUser = currentUserId != null
                    && reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId());

            AuthUser user = authUserResolverService.getUserInfoSafe(review.getUserId());
            String userName = user != null && user.getUsername() != null ? user.getUsername() : "User_" + review.getUserId();
            String userAvatar = user != null ? user.getAvatarUrl() : null;

            return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedByCurrentUser);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByUserId(Long userId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findVisibleByUserId(userId, pageable);

        AuthUser user = authUserResolverService.getUserInfoSafe(userId);
        String userName = user != null && user.getUsername() != null ? user.getUsername() : "User_" + userId;
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviews.map(r -> reviewMapper.toResponseWithUserInfo(r, userName, userAvatar, false));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewByPoiAndUser(Long poiId, Long userId) {
        Review review = reviewRepository.findByPoiIdAndUserId(poiId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found for POI: " + poiId + " and user: " + userId));

        AuthUser user = authUserResolverService.getUserInfoSafe(userId);
        String userName = user != null && user.getUsername() != null ? user.getUsername() : "User_" + userId;
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public ReviewResponse updateReview(Long id, Long userId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to update this review");
        }

        reviewMapper.updateEntity(review, request);
        Review updatedReview = reviewRepository.save(review);
        updatePoiRatingStats(updatedReview.getPoiId());

        AuthUser user = authUserResolverService.getUserInfoSafe(updatedReview.getUserId());
        String userName = user != null && user.getUsername() != null ? user.getUsername() : "User_" + updatedReview.getUserId();
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviewMapper.toResponseWithUserInfo(updatedReview, userName, userAvatar, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public void deleteReview(Long id, Long userId) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to delete this review");
        }

        Long poiId = review.getPoiId();
        reviewRepository.delete(review);
        updatePoiRatingStats(poiId);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public void hideReview(Long id, Long moderatorId) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        review.setIsHidden(true);
        reviewRepository.save(review);
        updatePoiRatingStats(review.getPoiId());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public void unhideReview(Long id, Long moderatorId) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        review.setIsHidden(false);
        reviewRepository.save(review);
        updatePoiRatingStats(review.getPoiId());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public ReviewResponse toggleLike(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        var existingLike = reviewLikeRepository.findByUserIdAndReviewId(userId, reviewId);

        boolean likedNow;
        if (existingLike.isPresent()) {
            reviewLikeRepository.delete(existingLike.get());
            likedNow = false;
        } else {
            ReviewLike like = ReviewLike.builder()
                    .userId(userId)
                    .review(review)
                    .build();
            reviewLikeRepository.save(like);
            likedNow = true;
        }

        Long likesCount = reviewLikeRepository.countByReviewId(reviewId);
        review.setLikesCount(likesCount.intValue());
        reviewRepository.save(review);

        AuthUser author = authUserResolverService.getUserInfoSafe(review.getUserId());
        String userName = author != null && author.getUsername() != null ? author.getUsername() : "User_" + review.getUserId();
        String userAvatar = author != null ? author.getAvatarUrl() : null;

        return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedNow);
    }

    @Override
    @Transactional(readOnly = true)
    public PoiReviewStatsResponse getPoiReviewStats(Long poiId) {
        Double averageRating = reviewRepository.calculateAverageRating(poiId);
        Long totalReviews = reviewRepository.countVisibleReviews(poiId);

        return PoiReviewStatsResponse.builder()
                .poiId(poiId)
                .totalReviews(totalReviews)
                .averageRating(averageRating != null ? Math.round(averageRating * 100.0) / 100.0 : 0.0)
                .visibleReviews(totalReviews)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getReviewSummary(Long moderatorId) {
        long totalReviews = reviewRepository.count();

        return ReviewSummaryResponse.builder()
                .totalReviews(totalReviews)
                .averageRating(0.0)
                .recentReviews24h(0L)
                .hiddenReviews(0L)
                .build();
    }

    @Override
    public boolean hasUserReviewedPoi(Long userId, Long poiId) {
        return reviewRepository.existsByPoiIdAndUserId(poiId, userId);
    }

    @Override
    @Transactional
    public void updatePoiRatingStats(Long poiId) {
        Double averageRating = reviewRepository.calculateAverageRating(poiId);
        Long reviewCount = reviewRepository.countVisibleReviews(poiId);

        Map<String, Object> ratingUpdate = new HashMap<>();
        ratingUpdate.put("averageRating", averageRating != null ? averageRating : 0.0);
        ratingUpdate.put("ratingCount", reviewCount != null ? reviewCount : 0L);

        poiClient.updatePoiRating(poiId, ratingUpdate);
    }
}