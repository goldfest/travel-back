package com.travelapp.review.validation;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validateCreateReview_shouldHaveNoViolations_whenRequestIsCorrect() {
        CreateReviewRequest request = validCreateReviewRequest();

        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validateCreateReview_shouldReturnViolation_whenPoiIdIsNull() {
        CreateReviewRequest request = validCreateReviewRequest();
        request.setPoiId(null);

        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("poiId");
                    assertThat(violation.getMessage()).contains("POI ID is required");
                });
    }

    @Test
    void validateCreateReview_shouldReturnViolation_whenRatingIsNull() {
        CreateReviewRequest request = validCreateReviewRequest();
        request.setRating(null);

        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("rating");
                    assertThat(violation.getMessage()).contains("Rating is required");
                });
    }

    @Test
    void validateCreateReview_shouldReturnViolation_whenRatingIsLessThanOne() {
        CreateReviewRequest request = validCreateReviewRequest();
        request.setRating((short) 0);

        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("rating");
                    assertThat(violation.getMessage()).contains("Rating must be at least 1");
                });
    }

    @Test
    void validateCreateReview_shouldReturnViolation_whenRatingIsGreaterThanFive() {
        CreateReviewRequest request = validCreateReviewRequest();
        request.setRating((short) 6);

        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("rating");
                    assertThat(violation.getMessage()).contains("Rating must be at most 5");
                });
    }

    @Test
    void validateCreateReview_shouldReturnViolation_whenCommentIsTooLong() {
        CreateReviewRequest request = validCreateReviewRequest();
        request.setComment("a".repeat(1001));

        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("comment");
                    assertThat(violation.getMessage()).contains("Comment must be less than 1000 characters");
                });
    }

    @Test
    void validateCreateReport_shouldHaveNoViolations_whenRequestIsCorrect() {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportType("incorrect_info")
                .comment("Некорректный адрес")
                .poiId(100L)
                .build();

        Set<ConstraintViolation<CreateReportRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validateCreateReport_shouldReturnViolation_whenReportTypeIsNull() {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportType(null)
                .comment("Некорректный адрес")
                .poiId(100L)
                .build();

        Set<ConstraintViolation<CreateReportRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("reportType");
                    assertThat(violation.getMessage()).contains("Report type is required");
                });
    }

    @Test
    void validateCreateReport_shouldReturnViolation_whenCommentIsTooLong() {
        CreateReportRequest request = CreateReportRequest.builder()
                .reportType("incorrect_info")
                .comment("a".repeat(1001))
                .poiId(100L)
                .build();

        Set<ConstraintViolation<CreateReportRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("comment");
                    assertThat(violation.getMessage()).contains("Comment must be less than 1000 characters");
                });
    }

    @Test
    void validateUpdateReview_shouldReturnViolation_whenRatingIsGreaterThanFive() {
        UpdateReviewRequest request = UpdateReviewRequest.builder()
                .rating((short) 6)
                .comment("Обновленный отзыв")
                .build();

        Set<ConstraintViolation<UpdateReviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("rating"));
    }

    @Test
    void validateUpdateReport_shouldReturnViolation_whenPhotoUrlIsTooLong() {
        UpdateReportRequest request = new UpdateReportRequest();
        request.setPhotoUrl("https://example.com/" + "a".repeat(501));

        Set<ConstraintViolation<UpdateReportRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("photoUrl"));
    }

    private CreateReviewRequest validCreateReviewRequest() {
        return CreateReviewRequest.builder()
                .poiId(100L)
                .rating((short) 5)
                .comment("Отличное место")
                .build();
    }
}
