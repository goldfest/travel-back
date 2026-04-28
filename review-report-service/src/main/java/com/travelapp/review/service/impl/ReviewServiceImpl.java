package com.travelapp.review.service.impl;

import com.travelapp.review.client.PoiClient;
import com.travelapp.review.exception.ResourceNotFoundException;
import com.travelapp.review.mapper.ReviewMapper;
import com.travelapp.review.model.dto.InternalUserResponse;
import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import com.travelapp.review.model.dto.response.PoiReviewStatsResponse;
import com.travelapp.review.model.dto.response.ReviewResponse;
import com.travelapp.review.model.dto.response.ReviewSummaryResponse;
import com.travelapp.review.model.entity.Review;
import com.travelapp.review.model.entity.ReviewLike;
import com.travelapp.review.model.entity.ReviewMedia;
import com.travelapp.review.repository.ReviewLikeRepository;
import com.travelapp.review.repository.ReviewMediaRepository;
import com.travelapp.review.repository.ReviewRepository;
import com.travelapp.review.security.SecurityUtils;
import com.travelapp.review.service.AuthUserService;
import com.travelapp.review.service.ReviewService;
import com.travelapp.review.service.media.ReviewMediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewMediaRepository reviewMediaRepository;
    private final ReviewMapper reviewMapper;
    private final PoiClient poiClient;
    private final AuthUserService authUserService;
    private final ReviewMediaStorageService mediaStorageService;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviewStats", key = "#request.poiId"),
            @CacheEvict(value = "poiReviews", allEntries = true)
    })
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        validatePoiExists(request.getPoiId());
        validateUserHasNotReviewed(request.getPoiId(), userId);

        Review review = reviewMapper.toEntity(request);
        review.setUserId(userId);

        boolean hasExternalImages = request.getImageUrls() != null && !request.getImageUrls().isEmpty();
        applyInitialModerationState(review, hasExternalImages);

        Review savedReview = reviewRepository.save(review);

        if (hasExternalImages) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }

                ReviewMedia media = ReviewMedia.builder()
                        .review(savedReview)
                        .imageUrl(imageUrl.trim())
                        .sourceType(ReviewMedia.SourceType.USER_UPLOAD)
                        .moderationStatus(ReviewMedia.ModerationStatus.PENDING)
                        .userId(userId)
                        .build();

                savedReview.addMedia(media);
            }
            savedReview = reviewRepository.save(savedReview);
        }

        return toReviewResponse(savedReview, userId, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviewStats", key = "#poiId"),
            @CacheEvict(value = "poiReviews", allEntries = true)
    })
    public ReviewResponse createReviewWithMedia(Long userId, Long poiId, Short rating, String comment, List<MultipartFile> files) {
        validatePoiExists(poiId);
        validateUserHasNotReviewed(poiId, userId);

        Review review = new Review();
        review.setPoiId(poiId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setComment(comment);
        applyInitialModerationState(review, files != null && !files.isEmpty());

        Review savedReview = reviewRepository.save(review);

        List<ReviewMedia> media = mediaStorageService.createReviewMedia(
                savedReview,
                userId,
                files,
                ReviewMedia.ModerationStatus.PENDING
        );

        for (ReviewMedia mediaItem : media) {
            savedReview.addMedia(mediaItem);
        }

        savedReview = reviewRepository.save(savedReview);
        return toReviewResponse(savedReview, userId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long id, Long currentUserId) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        validateReviewCanBeViewed(review, currentUserId);

        boolean likedByCurrentUser = currentUserId != null
                && reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, id);

        return toReviewResponse(review, currentUserId, likedByCurrentUser);
    }

    private Pageable sanitizeReviewPageable(Pageable pageable) {
        Set<String> allowedSortFields = Set.of(
                "id",
                "rating",
                "createdAt",
                "updatedAt",
                "likesCount"
        );

        Sort safeSort = Sort.by(
                pageable.getSort().stream()
                        .filter(order -> allowedSortFields.contains(order.getProperty()))
                        .map(order -> new Sort.Order(order.getDirection(), order.getProperty()))
                        .toList()
        );

        if (safeSort.isUnsorted()) {
            safeSort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                safeSort
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByPoiId(Long poiId, Long currentUserId, Pageable pageable) {
        Pageable safePageable = sanitizeReviewPageable(pageable);

        Page<Review> reviews = reviewRepository.findByPoiIdAndIsHiddenFalseAndModerationStatus(
                poiId,
                Review.ModerationStatus.APPROVED,
                safePageable
        );

        return reviews.map(review -> {
            boolean likedByCurrentUser = currentUserId != null
                    && reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId());

            return toReviewResponse(review, currentUserId, likedByCurrentUser);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByUserId(Long userId, Pageable pageable) {
        Pageable safePageable = sanitizeReviewPageable(pageable);

        Page<Review> reviews = reviewRepository.findVisibleByUserId(
                userId,
                Review.ModerationStatus.APPROVED,
                safePageable
        );

        InternalUserResponse user = resolveUser(userId);
        String userName = user != null && user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : "User_" + userId;
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviews.map(r -> reviewMapper.toResponseWithUserInfo(r, userName, userAvatar, false));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getPendingReviews(Pageable pageable) {
        Pageable safePageable = sanitizeReviewPageable(pageable);

        return reviewRepository.findByModerationStatusOrderByCreatedAtAsc(
                Review.ModerationStatus.PENDING,
                safePageable
        ).map(review -> toReviewResponse(review, null, false));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewByPoiAndUser(Long poiId, Long userId) {
        Review review = reviewRepository.findByPoiIdAndUserId(poiId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found for POI: " + poiId + " and user: " + userId));

        return toReviewResponse(review, userId, false);
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

        if (request.getImageIdsToRemove() != null && !request.getImageIdsToRemove().isEmpty()) {
            review.getMedia().removeIf(media -> request.getImageIdsToRemove().contains(media.getId()));
        }

        if (request.getImageUrlsToAdd() != null && !request.getImageUrlsToAdd().isEmpty()) {
            for (String imageUrl : request.getImageUrlsToAdd()) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }

                ReviewMedia media = ReviewMedia.builder()
                        .imageUrl(imageUrl.trim())
                        .sourceType(ReviewMedia.SourceType.USER_UPLOAD)
                        .moderationStatus(ReviewMedia.ModerationStatus.PENDING)
                        .userId(userId)
                        .review(review)
                        .build();

                review.addMedia(media);
            }

            review.setIsHidden(true);
            review.setModerationStatus(Review.ModerationStatus.PENDING);
            review.setModerationComment(null);
            review.setModeratedAt(null);
            review.setModeratedByUserId(null);
        }

        Review updatedReview = reviewRepository.save(review);
        return toReviewResponse(updatedReview, userId, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public ReviewResponse addMediaToReview(Long id, Long userId, List<MultipartFile> files) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to update this review");
        }

        List<ReviewMedia> media = mediaStorageService.createReviewMedia(
                review,
                userId,
                files,
                ReviewMedia.ModerationStatus.PENDING
        );

        for (ReviewMedia mediaItem : media) {
            review.addMedia(mediaItem);
        }

        review.setIsHidden(true);
        review.setModerationStatus(Review.ModerationStatus.PENDING);
        review.setModerationComment(null);
        review.setModeratedAt(null);
        review.setModeratedByUserId(null);

        Review saved = reviewRepository.save(review);
        return toReviewResponse(saved, userId, false);
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

        boolean isModerator = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("MODERATOR");
        if (!review.getUserId().equals(userId) && !isModerator) {
            throw new SecurityException("User is not authorized to delete this review");
        }

        reviewRepository.delete(review);
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
        review.setModerationStatus(Review.ModerationStatus.REJECTED);
        review.setModeratedByUserId(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        reviewRepository.save(review);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public void unhideReview(Long id, Long moderatorId) {
        approveReview(id, moderatorId, null);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public ReviewResponse approveReview(Long id, Long moderatorId, String moderationComment) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        review.setIsHidden(false);
        review.setModerationStatus(Review.ModerationStatus.APPROVED);
        review.setModeratedByUserId(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        review.setModerationComment(moderationComment);

        for (ReviewMedia media : review.getMedia()) {
            media.setModerationStatus(ReviewMedia.ModerationStatus.APPROVED);
        }

        Review saved = reviewRepository.save(review);
        return toReviewResponse(saved, moderatorId, false);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiReviews", allEntries = true),
            @CacheEvict(value = "poiReviewStats", allEntries = true)
    })
    public ReviewResponse rejectReview(Long id, Long moderatorId, String moderationComment) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        review.setIsHidden(true);
        review.setModerationStatus(Review.ModerationStatus.REJECTED);
        review.setModeratedByUserId(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        review.setModerationComment(moderationComment);

        for (ReviewMedia media : review.getMedia()) {
            media.setModerationStatus(ReviewMedia.ModerationStatus.REJECTED);
        }

        Review saved = reviewRepository.save(review);
        return toReviewResponse(saved, moderatorId, false);
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

        if (Boolean.TRUE.equals(review.getIsHidden()) || review.getModerationStatus() != Review.ModerationStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved reviews can be liked");
        }

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

        return toReviewResponse(review, userId, likedNow);
    }

    @Override
    @Transactional(readOnly = true)
    public PoiReviewStatsResponse getPoiReviewStats(Long poiId) {
        Double averageRating = reviewRepository.calculateAverageRating(poiId, Review.ModerationStatus.APPROVED);
        Long totalReviews = reviewRepository.countVisibleReviews(poiId, Review.ModerationStatus.APPROVED);

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

    private void validatePoiExists(Long poiId) {
        boolean poiExists = poiClient.checkPoiExists(poiId);
        if (!poiExists) {
            throw new ResourceNotFoundException("POI not found with id: " + poiId);
        }
    }

    private void validateUserHasNotReviewed(Long poiId, Long userId) {
        if (reviewRepository.existsByPoiIdAndUserId(poiId, userId)) {
            throw new IllegalArgumentException("User has already reviewed this POI");
        }
    }

    private void applyInitialModerationState(Review review, boolean hasMedia) {
        review.setIsHidden(true);
        review.setModerationStatus(Review.ModerationStatus.PENDING);
        review.setModerationComment(null);
        review.setModeratedAt(null);
        review.setModeratedByUserId(null);
    }

    private void validateReviewCanBeViewed(Review review, Long currentUserId) {
        boolean isVisible = !Boolean.TRUE.equals(review.getIsHidden())
                && review.getModerationStatus() == Review.ModerationStatus.APPROVED;
        boolean isOwner = currentUserId != null && review.getUserId().equals(currentUserId);
        boolean isModerator = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("MODERATOR");

        if (!isVisible && !isOwner && !isModerator) {
            throw new SecurityException("Review is not available");
        }
    }

    private ReviewResponse toReviewResponse(Review review, Long currentUserId, boolean likedByCurrentUser) {
        InternalUserResponse user = resolveUser(review.getUserId());
        String userName = user != null && user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : "User_" + review.getUserId();
        String userAvatar = user != null ? user.getAvatarUrl() : null;

        return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedByCurrentUser);
    }

    private InternalUserResponse resolveUser(Long userId) {
        try {
            return authUserService.getUserInfo(userId);
        } catch (Exception e) {
            log.warn("Failed to resolve user info for userId={}", userId);
            return null;
        }
    }
}