package com.travelapp.personalization.validation;

import com.travelapp.personalization.model.dto.request.CollectionPoiRequest;
import com.travelapp.personalization.model.dto.request.CollectionRequest;
import com.travelapp.personalization.model.dto.request.FavoriteRequest;
import com.travelapp.personalization.model.dto.request.PresetFilterRequest;
import com.travelapp.personalization.model.dto.request.SearchHistoryRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalizationRequestValidationTest {

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
    void favoriteRequest_shouldHaveNoViolations_whenPoiIdIsPresent() {
        FavoriteRequest request = new FavoriteRequest(100L);

        Set<ConstraintViolation<FavoriteRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void favoriteRequest_shouldReturnViolation_whenPoiIdIsNull() {
        FavoriteRequest request = new FavoriteRequest(null);

        Set<ConstraintViolation<FavoriteRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("poiId"));
    }

    @Test
    void collectionRequest_shouldReturnViolation_whenNameIsBlank() {
        CollectionRequest request = new CollectionRequest("   ", "Описание", null);

        Set<ConstraintViolation<CollectionRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("name"));
    }

    @Test
    void collectionRequest_shouldReturnViolation_whenDescriptionIsTooLong() {
        CollectionRequest request = new CollectionRequest("Музеи", "a".repeat(1001), null);

        Set<ConstraintViolation<CollectionRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("description"));
    }

    @Test
    void collectionPoiRequest_shouldReturnViolation_whenPoiIdIsNull() {
        CollectionPoiRequest request = new CollectionPoiRequest(null, 1);

        Set<ConstraintViolation<CollectionPoiRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("poiId"));
    }

    @Test
    void presetFilterRequest_shouldHaveNoViolations_whenRequestIsCorrect() {
        PresetFilterRequest request = presetRequest();

        Set<ConstraintViolation<PresetFilterRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void presetFilterRequest_shouldReturnViolation_whenNameIsBlank() {
        PresetFilterRequest request = presetRequest();
        request.setName("   ");

        Set<ConstraintViolation<PresetFilterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("name"));
    }

    @Test
    void presetFilterRequest_shouldReturnViolation_whenFiltersJsonIsInvalid() {
        PresetFilterRequest request = presetRequest();
        request.setFiltersJson("{ratingFrom: }");

        Set<ConstraintViolation<PresetFilterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("filtersJson");
                    assertThat(violation.getMessage()).contains("Invalid JSON");
                });
    }

    @Test
    void presetFilterRequest_shouldReturnViolation_whenCityIdIsNull() {
        PresetFilterRequest request = presetRequest();
        request.setCityId(null);

        Set<ConstraintViolation<PresetFilterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("cityId"));
    }

    @Test
    void presetFilterRequest_shouldReturnViolation_whenPoiTypeIdIsNull() {
        PresetFilterRequest request = presetRequest();
        request.setPoiTypeId(null);

        Set<ConstraintViolation<PresetFilterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("poiTypeId"));
    }

    @Test
    void searchHistoryRequest_shouldReturnViolation_whenFiltersJsonIsInvalid() {
        SearchHistoryRequest request = new SearchHistoryRequest("музей", "{bad-json", 1L, null);

        Set<ConstraintViolation<SearchHistoryRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("filtersJson"));
    }

    @Test
    void validJsonValidator_shouldAllowNullAndBlankValues() {
        SearchHistoryRequest request = new SearchHistoryRequest("музей", "   ", 1L, null);

        Set<ConstraintViolation<SearchHistoryRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    private PresetFilterRequest presetRequest() {
        return new PresetFilterRequest(
                "Музеи рядом",
                "{\"ratingFrom\":4,\"openNow\":true}",
                1L,
                2L
        );
    }
}
