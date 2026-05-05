package com.travelapp.notification.validation;

import com.travelapp.notification.model.dto.request.CreateNotificationRequest;
import com.travelapp.notification.model.dto.request.NotificationFilterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRequestValidationTest {

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
    void validate_shouldHaveNoViolations_whenCreateRequestIsCorrect() {
        CreateNotificationRequest request = validRequest();

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_shouldReturnViolation_whenTypeIsBlank() {
        CreateNotificationRequest request = validRequest();
        request.setType("   ");

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("type");
                    assertThat(violation.getMessage()).contains("Тип уведомления обязателен");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenTypeIsTooLong() {
        CreateNotificationRequest request = validRequest();
        request.setType("a".repeat(51));

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("type");
                    assertThat(violation.getMessage()).contains("Тип уведомления не должен превышать 50 символов");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenTitleIsBlank() {
        CreateNotificationRequest request = validRequest();
        request.setTitle(" ");

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("title");
                    assertThat(violation.getMessage()).contains("Заголовок уведомления обязателен");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenTitleIsTooLong() {
        CreateNotificationRequest request = validRequest();
        request.setTitle("a".repeat(256));

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("title");
                    assertThat(violation.getMessage()).contains("Заголовок уведомления не должен превышать 255 символов");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenDescriptionIsTooLong() {
        CreateNotificationRequest request = validRequest();
        request.setDescription("a".repeat(501));

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("description");
                    assertThat(violation.getMessage()).contains("Описание уведомления не должно превышать 500 символов");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenUserIdIsNull() {
        CreateNotificationRequest request = validRequest();
        request.setUserId(null);

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("userId");
                    assertThat(violation.getMessage()).contains("ID пользователя обязателен");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenEventKeyIsTooLong() {
        CreateNotificationRequest request = validRequest();
        request.setEventKey("a".repeat(121));

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("eventKey");
                    assertThat(violation.getMessage()).contains("eventKey не должен превышать 120 символов");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenDeliveryChannelIsTooLong() {
        CreateNotificationRequest request = validRequest();
        request.setDeliveryChannel("a".repeat(21));

        Set<ConstraintViolation<CreateNotificationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("deliveryChannel");
                    assertThat(violation.getMessage()).contains("deliveryChannel не должен превышать 20 символов");
                });
    }

    @Test
    void notificationFilterRequest_shouldBuildPageableWithSpecifiedSort() {
        NotificationFilterRequest filter = new NotificationFilterRequest();
        filter.setPage(2);
        filter.setSize(5);
        filter.setSortBy("title");
        filter.setDirection(Sort.Direction.ASC);

        Pageable pageable = filter.toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("title")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void notificationFilterRequest_shouldUseDefaultPaginationValues() {
        NotificationFilterRequest filter = new NotificationFilterRequest();

        Pageable pageable = filter.toPageable();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    private CreateNotificationRequest validRequest() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setType("system");
        request.setTitle("Системное уведомление");
        request.setDescription("Описание уведомления");
        request.setUserId(10L);
        request.setEventKey("system-event-1");
        request.setDeliveryChannel("IN_APP");
        return request;
    }
}
