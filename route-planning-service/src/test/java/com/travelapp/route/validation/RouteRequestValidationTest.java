package com.travelapp.route.validation;

import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteDayCreateRequest;
import com.travelapp.route.model.dto.request.RouteGenerateRequest;
import com.travelapp.route.model.dto.request.RoutePointCreateRequest;
import com.travelapp.route.model.dto.request.RoutePointRequest;
import com.travelapp.route.model.dto.request.RouteUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RouteRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void routeCreateRequest_shouldBeValid_whenRequiredFieldsAreFilled() {
        RouteCreateRequest request = validRouteCreateRequest();

        Set<ConstraintViolation<RouteCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void routeCreateRequest_shouldReturnViolations_whenNameCityAndDaysAreInvalid() {
        RouteCreateRequest request = validRouteCreateRequest();
        request.setName("   ");
        request.setCityId(null);
        request.setDays(List.of());

        Set<ConstraintViolation<RouteCreateRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("name", "cityId", "days");
    }

    @Test
    void routePointCreateRequest_shouldReturnViolation_whenVisitDurationIsTooSmall() {
        RoutePointCreateRequest request = validPointCreateRequest();
        request.setEstimatedVisitMinutes(4);

        Set<ConstraintViolation<RoutePointCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("estimatedVisitMinutes"));
    }

    @Test
    void routePointCreateRequest_shouldReturnViolation_whenVisitDurationIsTooLarge() {
        RoutePointCreateRequest request = validPointCreateRequest();
        request.setEstimatedVisitMinutes(1441);

        Set<ConstraintViolation<RoutePointCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("estimatedVisitMinutes"));
    }

    @Test
    void routePointRequest_shouldRequirePoiId() {
        RoutePointRequest request = new RoutePointRequest();

        Set<ConstraintViolation<RoutePointRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("poiId"));
    }

    @Test
    void routeGenerateRequest_shouldValidateDaysCountRange() {
        RouteGenerateRequest request = new RouteGenerateRequest();
        request.setCityId(10L);
        request.setDaysCount(15);

        Set<ConstraintViolation<RouteGenerateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("daysCount"));
    }

    @Test
    void routeUpdateRequest_shouldReturnViolation_whenNameIsTooLong() {
        RouteUpdateRequest request = new RouteUpdateRequest();
        request.setName("a".repeat(256));

        Set<ConstraintViolation<RouteUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("name"));
    }

    private RouteCreateRequest validRouteCreateRequest() {
        RouteDayCreateRequest day = new RouteDayCreateRequest();
        day.setDayNumber((short) 1);
        day.setPoints(List.of(validPointCreateRequest()));

        RouteCreateRequest request = new RouteCreateRequest();
        request.setName("Маршрут по Ульяновску");
        request.setCityId(10L);
        request.setDays(List.of(day));
        return request;
    }

    private RoutePointCreateRequest validPointCreateRequest() {
        RoutePointCreateRequest point = new RoutePointCreateRequest();
        point.setPoiId(100L);
        point.setOrderIndex((short) 1);
        point.setEstimatedVisitMinutes(60);
        return point;
    }
}
