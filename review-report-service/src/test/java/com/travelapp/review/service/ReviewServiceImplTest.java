package com.travelapp.review.service;

import com.travelapp.review.client.PoiClient;
import com.travelapp.review.exception.ResourceNotFoundException;
import com.travelapp.review.mapper.ReviewMapper;
import com.travelapp.review.model.dto.InternalUserResponse;
import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.response.PoiReviewStatsResponse;
import com.travelapp.review.model.dto.response.ReviewResponse;
import com.travelapp.review.model.entity.Review;
import com.travelapp.review.model.entity.ReviewLike;
import com.travelapp.review.model.entity.ReviewMedia;
import com.travelapp.review.repository.ReviewLikeRepository;
import com.travelapp.review.repository.ReviewMediaRepository;
import com.travelapp.review.repository.ReviewRepository;
import com.travelapp.review.service.impl.ReviewServiceImpl;
import com.travelapp.review.service.media.ReviewMediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewLikeRepository reviewLikeRepository;

    @Mock
    private ReviewMediaRepository reviewMediaRepository;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private PoiClient poiClient;

    @Mock
    private AuthUserService authUserService;

    @Mock
    private ReviewMediaStorageService mediaStorageService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createReview_shouldCreatePendingHiddenReview_whenPoiExistsAndUserHasNotReviewed() {
        Long userId = 10L;
        CreateReviewRequest request = createReviewRequest(100L, (short) 5, "Отличное место");
        Review review = reviewEntity(null, 100L, null, (short) 5, Review.ModerationStatus.APPROVED, false);
        Review savedReview = reviewEntity(1L, 100L, userId, (short) 5, Review.ModerationStatus.PENDING, true);
        ReviewResponse response = reviewResponse(1L, 100L, userId, Review.ModerationStatus.PENDING, true, false);

        when(poiClient.checkPoiExists(100L)).thenReturn(true);
        when(reviewRepository.existsByPoiIdAndUserId(100L, userId)).thenReturn(false);
        when(reviewMapper.toEntity(request)).thenReturn(review);
        when(reviewRepository.save(review)).thenReturn(savedReview);
        when(authUserService.getUserInfo(userId)).thenReturn(userResponse(userId, "stepan"));
        when(reviewMapper.toResponseWithUserInfo(savedReview, "stepan", "https://example.com/avatar.png", false))
                .thenReturn(response);

        ReviewResponse result = reviewService.createReview(userId, request);

        assertThat(result).isEqualTo(response);
        assertThat(review.getUserId()).isEqualTo(userId);
        assertThat(review.getIsHidden()).isTrue();
        assertThat(review.getModerationStatus()).isEqualTo(Review.ModerationStatus.PENDING);
        assertThat(review.getModerationComment()).isNull();
        assertThat(review.getModeratedAt()).isNull();
        assertThat(review.getModeratedByUserId()).isNull();

        verify(poiClient).checkPoiExists(100L);
        verify(reviewRepository).existsByPoiIdAndUserId(100L, userId);
        verify(reviewRepository).save(review);
    }

    @Test
    void createReview_shouldAddExternalImagesAsPendingMedia_whenImageUrlsAreProvided() {
        Long userId = 10L;
        CreateReviewRequest request = createReviewRequest(100L, (short) 4, "Красиво");
        request.setImageUrls(List.of(" https://example.com/1.jpg ", "   ", "https://example.com/2.jpg"));

        Review review = reviewEntity(null, 100L, null, (short) 4, Review.ModerationStatus.APPROVED, false);
        Review firstSavedReview = reviewEntity(1L, 100L, userId, (short) 4, Review.ModerationStatus.PENDING, true);
        Review secondSavedReview = reviewEntity(1L, 100L, userId, (short) 4, Review.ModerationStatus.PENDING, true);
        ReviewResponse response = reviewResponse(1L, 100L, userId, Review.ModerationStatus.PENDING, true, false);

        when(poiClient.checkPoiExists(100L)).thenReturn(true);
        when(reviewRepository.existsByPoiIdAndUserId(100L, userId)).thenReturn(false);
        when(reviewMapper.toEntity(request)).thenReturn(review);
        when(reviewRepository.save(review)).thenReturn(firstSavedReview);
        when(reviewRepository.save(firstSavedReview)).thenReturn(secondSavedReview);
        when(authUserService.getUserInfo(userId)).thenReturn(userResponse(userId, "stepan"));
        when(reviewMapper.toResponseWithUserInfo(secondSavedReview, "stepan", "https://example.com/avatar.png", false))
                .thenReturn(response);

        ReviewResponse result = reviewService.createReview(userId, request);

        assertThat(result).isEqualTo(response);
        assertThat(firstSavedReview.getMedia()).hasSize(2);
        assertThat(firstSavedReview.getMedia())
                .extracting(ReviewMedia::getImageUrl)
                .containsExactly("https://example.com/1.jpg", "https://example.com/2.jpg");
        assertThat(firstSavedReview.getMedia())
                .allSatisfy(media -> {
                    assertThat(media.getModerationStatus()).isEqualTo(ReviewMedia.ModerationStatus.PENDING);
                    assertThat(media.getSourceType()).isEqualTo(ReviewMedia.SourceType.USER_UPLOAD);
                    assertThat(media.getUserId()).isEqualTo(userId);
                    assertThat(media.getReview()).isEqualTo(firstSavedReview);
                });

        verify(reviewRepository, times(2)).save(any(Review.class));
    }

    @Test
    void createReview_shouldThrowResourceNotFoundException_whenPoiDoesNotExist() {
        Long userId = 10L;
        CreateReviewRequest request = createReviewRequest(404L, (short) 5, "Комментарий");

        when(poiClient.checkPoiExists(404L)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("POI not found with id: 404");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_shouldThrowIllegalArgumentException_whenUserAlreadyReviewedPoi() {
        Long userId = 10L;
        CreateReviewRequest request = createReviewRequest(100L, (short) 5, "Комментарий");

        when(poiClient.checkPoiExists(100L)).thenReturn(true);
        when(reviewRepository.existsByPoiIdAndUserId(100L, userId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User has already reviewed this POI");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void approveReview_shouldMakeReviewVisibleAndApproveAllMedia() {
        Long reviewId = 1L;
        Long moderatorId = 99L;
        Review review = reviewEntity(reviewId, 100L, 10L, (short) 5, Review.ModerationStatus.PENDING, true);
        ReviewMedia media = ReviewMedia.builder()
                .id(5L)
                .imageUrl("https://example.com/review.jpg")
                .moderationStatus(ReviewMedia.ModerationStatus.PENDING)
                .build();
        review.addMedia(media);
        ReviewResponse response = reviewResponse(reviewId, 100L, 10L, Review.ModerationStatus.APPROVED, false, false);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);
        when(authUserService.getUserInfo(10L)).thenReturn(userResponse(10L, "stepan"));
        when(reviewMapper.toResponseWithUserInfo(review, "stepan", "https://example.com/avatar.png", false))
                .thenReturn(response);

        ReviewResponse result = reviewService.approveReview(reviewId, moderatorId, "Проверено");

        assertThat(result).isEqualTo(response);
        assertThat(review.getIsHidden()).isFalse();
        assertThat(review.getModerationStatus()).isEqualTo(Review.ModerationStatus.APPROVED);
        assertThat(review.getModeratedByUserId()).isEqualTo(moderatorId);
        assertThat(review.getModeratedAt()).isNotNull();
        assertThat(review.getModerationComment()).isEqualTo("Проверено");
        assertThat(media.getModerationStatus()).isEqualTo(ReviewMedia.ModerationStatus.APPROVED);
        verify(reviewRepository).save(review);
    }

    @Test
    void rejectReview_shouldHideReviewAndRejectAllMedia() {
        Long reviewId = 1L;
        Long moderatorId = 99L;
        Review review = reviewEntity(reviewId, 100L, 10L, (short) 5, Review.ModerationStatus.PENDING, true);
        ReviewMedia media = ReviewMedia.builder()
                .id(5L)
                .imageUrl("https://example.com/review.jpg")
                .moderationStatus(ReviewMedia.ModerationStatus.PENDING)
                .build();
        review.addMedia(media);
        ReviewResponse response = reviewResponse(reviewId, 100L, 10L, Review.ModerationStatus.REJECTED, true, false);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);
        when(authUserService.getUserInfo(10L)).thenReturn(userResponse(10L, "stepan"));
        when(reviewMapper.toResponseWithUserInfo(review, "stepan", "https://example.com/avatar.png", false))
                .thenReturn(response);

        ReviewResponse result = reviewService.rejectReview(reviewId, moderatorId, "Некорректный отзыв");

        assertThat(result).isEqualTo(response);
        assertThat(review.getIsHidden()).isTrue();
        assertThat(review.getModerationStatus()).isEqualTo(Review.ModerationStatus.REJECTED);
        assertThat(review.getModeratedByUserId()).isEqualTo(moderatorId);
        assertThat(review.getModeratedAt()).isNotNull();
        assertThat(review.getModerationComment()).isEqualTo("Некорректный отзыв");
        assertThat(media.getModerationStatus()).isEqualTo(ReviewMedia.ModerationStatus.REJECTED);
        verify(reviewRepository).save(review);
    }

    @Test
    void toggleLike_shouldCreateLikeAndUpdateLikesCount_whenReviewIsApprovedAndNotLikedYet() {
        Long reviewId = 1L;
        Long userId = 20L;
        Review review = reviewEntity(reviewId, 100L, 10L, (short) 5, Review.ModerationStatus.APPROVED, false);
        ReviewResponse response = reviewResponse(reviewId, 100L, 10L, Review.ModerationStatus.APPROVED, false, true);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewLikeRepository.findByUserIdAndReviewId(userId, reviewId)).thenReturn(Optional.empty());
        when(reviewLikeRepository.countByReviewId(reviewId)).thenReturn(1L);
        when(authUserService.getUserInfo(10L)).thenReturn(userResponse(10L, "stepan"));
        when(reviewMapper.toResponseWithUserInfo(review, "stepan", "https://example.com/avatar.png", true))
                .thenReturn(response);

        ReviewResponse result = reviewService.toggleLike(reviewId, userId);

        assertThat(result).isEqualTo(response);
        assertThat(review.getLikesCount()).isEqualTo(1);
        verify(reviewLikeRepository).save(any(ReviewLike.class));
        verify(reviewRepository).save(review);
    }

    @Test
    void toggleLike_shouldRemoveLikeAndUpdateLikesCount_whenReviewIsAlreadyLiked() {
        Long reviewId = 1L;
        Long userId = 20L;
        Review review = reviewEntity(reviewId, 100L, 10L, (short) 5, Review.ModerationStatus.APPROVED, false);
        ReviewLike existingLike = ReviewLike.builder().id(7L).userId(userId).review(review).build();
        ReviewResponse response = reviewResponse(reviewId, 100L, 10L, Review.ModerationStatus.APPROVED, false, false);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewLikeRepository.findByUserIdAndReviewId(userId, reviewId)).thenReturn(Optional.of(existingLike));
        when(reviewLikeRepository.countByReviewId(reviewId)).thenReturn(0L);
        when(authUserService.getUserInfo(10L)).thenReturn(userResponse(10L, "stepan"));
        when(reviewMapper.toResponseWithUserInfo(review, "stepan", "https://example.com/avatar.png", false))
                .thenReturn(response);

        ReviewResponse result = reviewService.toggleLike(reviewId, userId);

        assertThat(result).isEqualTo(response);
        assertThat(review.getLikesCount()).isZero();
        verify(reviewLikeRepository).delete(existingLike);
        verify(reviewRepository).save(review);
    }

    @Test
    void toggleLike_shouldThrowIllegalArgumentException_whenReviewIsPending() {
        Long reviewId = 1L;
        Review review = reviewEntity(reviewId, 100L, 10L, (short) 5, Review.ModerationStatus.PENDING, true);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.toggleLike(reviewId, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only approved reviews can be liked");

        verify(reviewLikeRepository, never()).save(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void getPoiReviewStats_shouldReturnRoundedAverageRatingAndVisibleReviewCount() {
        Long poiId = 100L;
        when(reviewRepository.calculateAverageRating(poiId, Review.ModerationStatus.APPROVED)).thenReturn(4.6666);
        when(reviewRepository.countVisibleReviews(poiId, Review.ModerationStatus.APPROVED)).thenReturn(3L);

        PoiReviewStatsResponse result = reviewService.getPoiReviewStats(poiId);

        assertThat(result.getPoiId()).isEqualTo(poiId);
        assertThat(result.getTotalReviews()).isEqualTo(3L);
        assertThat(result.getVisibleReviews()).isEqualTo(3L);
        assertThat(result.getAverageRating()).isEqualTo(4.67);
    }

    @Test
    void deleteReview_shouldDeleteReview_whenUserIsOwner() {
        Long reviewId = 1L;
        Long userId = 10L;
        Review review = reviewEntity(reviewId, 100L, userId, (short) 5, Review.ModerationStatus.APPROVED, false);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        reviewService.deleteReview(reviewId, userId);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_shouldThrowSecurityException_whenUserIsNotOwner() {
        Long reviewId = 1L;
        Review review = reviewEntity(reviewId, 100L, 10L, (short) 5, Review.ModerationStatus.APPROVED, false);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(reviewId, 20L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("User is not authorized to delete this review");

        verify(reviewRepository, never()).delete(any());
    }

    private CreateReviewRequest createReviewRequest(Long poiId, Short rating, String comment) {
        return CreateReviewRequest.builder()
                .poiId(poiId)
                .rating(rating)
                .comment(comment)
                .build();
    }

    private Review reviewEntity(Long id,
                                Long poiId,
                                Long userId,
                                Short rating,
                                Review.ModerationStatus status,
                                Boolean hidden) {
        return Review.builder()
                .id(id)
                .poiId(poiId)
                .userId(userId)
                .rating(rating)
                .comment("Комментарий")
                .isHidden(hidden)
                .moderationStatus(status)
                .likesCount(0)
                .media(new ArrayList<>())
                .likes(new ArrayList<>())
                .build();
    }

    private ReviewResponse reviewResponse(Long id,
                                          Long poiId,
                                          Long userId,
                                          Review.ModerationStatus status,
                                          Boolean hidden,
                                          Boolean likedByCurrentUser) {
        return ReviewResponse.builder()
                .id(id)
                .poiId(poiId)
                .userId(userId)
                .rating((short) 5)
                .comment("Комментарий")
                .isHidden(hidden)
                .moderationStatus(status)
                .likesCount(0)
                .likedByCurrentUser(likedByCurrentUser)
                .userName("stepan")
                .userAvatar("https://example.com/avatar.png")
                .build();
    }

    private InternalUserResponse userResponse(Long id, String username) {
        InternalUserResponse user = new InternalUserResponse();
        user.setId(id);
        user.setUsername(username);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setStatus("ACTIVE");
        user.setRole("USER");
        user.setIsBlocked(false);
        return user;
    }
}
