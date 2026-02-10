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
import com.travelapp.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Override
    @Transactional
    @CacheEvict(value = {"poiReviews", "poiReviewStats"}, key = "#request.poiId")
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        log.info("Creating review for POI: {} by user: {}", request.getPoiId(), userId);

        // Проверяем, существует ли POI (через вызов другого сервиса)
        boolean poiExists = poiClient.checkPoiExists(request.getPoiId());
        if (!poiExists) {
            throw new ResourceNotFoundException("POI not found with id: " + request.getPoiId());
        }

        // Проверяем, не оставлял ли пользователь уже отзыв
        if (reviewRepository.existsByPoiIdAndUserId(request.getPoiId(), userId)) {
            throw new IllegalArgumentException("User has already reviewed this POI");
        }

        Review review = reviewMapper.toEntity(request);
        review.setUserId(userId);

        // Добавляем медиа, если есть
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            request.getImageUrls().forEach(imageUrl -> {
                // Здесь будет создание ReviewMedia объектов
            });
        }

        Review savedReview = reviewRepository.save(review);

        // Обновляем статистику POI
        updatePoiRatingStats(request.getPoiId());

        log.info("Review created with id: {}", savedReview.getId());
        return reviewMapper.toResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long id, Long currentUserId) {
        log.info("Fetching review by id: {}", id);

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        Boolean likedByCurrentUser = currentUserId != null ?
                reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, id) : false;

        // Получаем информацию о пользователе через auth service (в реальном проекте)
        String userName = "User_" + review.getUserId(); // заглушка
        String userAvatar = null;

        return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedByCurrentUser);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "poiReviews", key = "#poiId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<ReviewResponse> getReviewsByPoiId(Long poiId, Long currentUserId, Pageable pageable) {
        log.info("Fetching reviews for POI: {} with page: {}", poiId, pageable);

        Page<Review> reviews = reviewRepository.findByPoiIdAndIsHiddenFalse(poiId, pageable);

        return reviews.map(review -> {
            Boolean likedByCurrentUser = currentUserId != null ?
                    reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId()) : false;

            String userName = "User_" + review.getUserId(); // заглушка
            String userAvatar = null;

            return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedByCurrentUser);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByUserId(Long userId, Pageable pageable) {
        log.info("Fetching reviews by user: {} with page: {}", userId, pageable);

        Page<Review> reviews = reviewRepository.findVisibleByUserId(userId, pageable);

        return reviews.map(reviewMapper::toResponse);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiReviews", "poiReviewStats"}, key = "#review.poiId")
    public ReviewResponse updateReview(Long id, Long userId, UpdateReviewRequest request) {
        log.info("Updating review: {} by user: {}", id, userId);

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        // Проверяем права на редактирование
        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to update this review");
        }

        reviewMapper.updateEntity(review, request);
        Review updatedReview = reviewRepository.save(review);

        // Обновляем статистику POI
        updatePoiRatingStats(review.getPoiId());

        log.info("Review updated: {}", id);
        return reviewMapper.toResponse(updatedReview);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiReviews", "poiReviewStats"}, key = "#review.poiId")
    public void deleteReview(Long id, Long userId) {
        log.info("Deleting review: {} by user: {}", id, userId);

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        // Проверяем права на удаление
        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to delete this review");
        }

        Long poiId = review.getPoiId();
        reviewRepository.delete(review);

        // Обновляем статистику POI
        updatePoiRatingStats(poiId);

        log.info("Review deleted: {}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiReviews", "poiReviewStats"}, key = "#review.poiId")
    public void hideReview(Long id, Long moderatorId) {
        log.info("Hiding review: {} by moderator: {}", id, moderatorId);

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        review.setIsHidden(true);
        reviewRepository.save(review);

        updatePoiRatingStats(review.getPoiId());

        log.info("Review hidden: {}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiReviews", "poiReviewStats"}, key = "#review.poiId")
    public void unhideReview(Long id, Long moderatorId) {
        log.info("Unhiding review: {} by moderator: {}", id, moderatorId);

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        review.setIsHidden(false);
        reviewRepository.save(review);

        updatePoiRatingStats(review.getPoiId());

        log.info("Review unhidden: {}", id);
    }

    @Override
    @Transactional
    public ReviewResponse toggleLike(Long reviewId, Long userId) {
        log.info("Toggling like for review: {} by user: {}", reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        var existingLike = reviewLikeRepository.findByUserIdAndReviewId(userId, reviewId);

        if (existingLike.isPresent()) {
            // Убираем лайк
            reviewLikeRepository.delete(existingLike.get());
            log.info("Like removed for review: {} by user: {}", reviewId, userId);
        } else {
            // Добавляем лайк
            ReviewLike like = ReviewLike.builder()
                    .userId(userId)
                    .review(review)
                    .build();
            reviewLikeRepository.save(like);
            log.info("Like added for review: {} by user: {}", reviewId, userId);
        }

        // Обновляем счетчик лайков
        Long likesCount = reviewLikeRepository.countByReviewId(reviewId);
        review.setLikesCount(likesCount.intValue());
        reviewRepository.save(review);

        Boolean likedByCurrentUser = existingLike.isEmpty(); // После toggle меняется состояние

        String userName = "User_" + review.getUserId(); // заглушка
        String userAvatar = null;

        return reviewMapper.toResponseWithUserInfo(review, userName, userAvatar, likedByCurrentUser);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "poiReviewStats", key = "#poiId")
    public PoiReviewStatsResponse getPoiReviewStats(Long poiId) {
        log.info("Fetching review stats for POI: {}", poiId);

        Double averageRating = reviewRepository.calculateAverageRating(poiId);
        Long totalReviews = reviewRepository.countVisibleReviews(poiId);

        // Получаем распределение по рейтингам (упрощенный вариант)
        // В реальном проекте нужно добавить отдельный запрос для этого

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
        log.info("Fetching review summary for moderator: {}", moderatorId);

        // Подсчитываем общее количество отзывов
        long totalReviews = reviewRepository.count();

        // Отзывы за последние 24 часа
        long recentReviews24h = reviewRepository.count();
        // В реальном проекте: reviewRepository.countByCreatedAtAfter(LocalDateTime.now().minusHours(24))

        // Скрытые отзывы
        long hiddenReviews = reviewRepository.count();
        // В реальном проекте: reviewRepository.countByIsHiddenTrue()

        return ReviewSummaryResponse.builder()
                .totalReviews(totalReviews)
                .averageRating(4.5) // заглушка
                .recentReviews24h(recentReviews24h)
                .hiddenReviews(hiddenReviews)
                .build();
    }

    @Override
    public boolean hasUserReviewedPoi(Long userId, Long poiId) {
        return reviewRepository.existsByPoiIdAndUserId(poiId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewByPoiAndUser(Long poiId, Long userId) {
        Review review = reviewRepository.findByPoiIdAndUserId(poiId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found for POI: " + poiId + " and user: " + userId));

        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional
    public void updatePoiRatingStats(Long poiId) {
        log.info("Updating rating stats for POI: {}", poiId);

        Double averageRating = reviewRepository.calculateAverageRating(poiId);
        Long reviewCount = reviewRepository.countVisibleReviews(poiId);

        // Отправляем обновление в POI сервис
        Map<String, Object> ratingUpdate = new HashMap<>();
        ratingUpdate.put("averageRating", averageRating != null ? averageRating : 0.0);
        ratingUpdate.put("ratingCount", reviewCount != null ? reviewCount : 0L);

        poiClient.updatePoiRating(poiId, ratingUpdate);

        log.info("POI rating stats updated for POI: {}", poiId);
    }
}