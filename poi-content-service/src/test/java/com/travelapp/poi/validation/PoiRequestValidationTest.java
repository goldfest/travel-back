package com.travelapp.poi.validation;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiTypeRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.entity.PoiMedia;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PoiRequestValidationTest {

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
    void validateCreateRequest_shouldHaveNoViolations_whenRequestIsCorrect() {
        PoiCreateRequest request = validCreateRequest();

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validateCreateRequest_shouldReturnViolation_whenNameIsBlank() {
        PoiCreateRequest request = validCreateRequest();
        request.setName("   ");

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("name");
            assertThat(v.getMessage()).contains("POI name is required");
        });
    }

    @Test
    void validateCreateRequest_shouldReturnViolation_whenLatitudeIsOutOfRange() {
        PoiCreateRequest request = validCreateRequest();
        request.setLatitude(new BigDecimal("91.0"));

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("latitude"));
    }

    @Test
    void validateCreateRequest_shouldReturnViolation_whenLongitudeIsOutOfRange() {
        PoiCreateRequest request = validCreateRequest();
        request.setLongitude(new BigDecimal("181.0"));

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("longitude"));
    }

    @Test
    void validateCreateRequest_shouldReturnViolation_whenPhoneHasInvalidFormat() {
        PoiCreateRequest request = validCreateRequest();
        request.setPhone("phone-number");

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("phone");
            assertThat(v.getMessage()).contains("Invalid phone number format");
        });
    }

    @Test
    void validateCreateRequest_shouldReturnNestedViolation_whenHourDayIsInvalid() {
        PoiCreateRequest request = validCreateRequest();
        request.getHours().get(0).setDayOfWeek((short) 7);

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("hours[0].dayOfWeek"));
    }

    @Test
    void validateCreateRequest_shouldReturnNestedViolation_whenMediaUrlIsBlank() {
        PoiCreateRequest request = validCreateRequest();
        request.getMedia().get(0).setUrl(" ");

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("media[0].url"));
    }

    @Test
    void validateCreateRequest_shouldReturnNestedViolation_whenConfidenceScoreIsGreaterThanOne() {
        PoiCreateRequest request = validCreateRequest();
        request.getSources().get(0).setConfidenceScore(new BigDecimal("1.5"));

        Set<ConstraintViolation<PoiCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("sources[0].confidenceScore"));
    }

    @Test
    void validateUpdateRequest_shouldReturnViolation_whenPriceLevelIsGreaterThanFour() {
        PoiUpdateRequest request = new PoiUpdateRequest();
        request.setPriceLevel((short) 5);

        Set<ConstraintViolation<PoiUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("priceLevel"));
    }

    @Test
    void validatePoiTypeRequest_shouldReturnViolation_whenCodeIsBlank() {
        PoiTypeRequest request = new PoiTypeRequest();
        request.setCode(" ");
        request.setName("Музей");

        Set<ConstraintViolation<PoiTypeRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("code"));
    }

    private PoiCreateRequest validCreateRequest() {
        PoiCreateRequest request = new PoiCreateRequest();
        request.setName("Ульяновский областной краеведческий музей");
        request.setSlug("ulyanovsk-kraevedcheskiy-muzey");
        request.setCityId(1L);
        request.setPoiTypeId(1L);
        request.setLatitude(new BigDecimal("54.314200"));
        request.setLongitude(new BigDecimal("48.403100"));
        request.setAddress("бульвар Новый Венец, 3/4");
        request.setDescription("Исторический музей с экспозициями о культуре и развитии Ульяновской области.");
        request.setPhone("+78422123456");
        request.setSiteUrl("https://example.com");
        request.setPriceLevel((short) 1);

        PoiCreateRequest.HoursRequest hours = new PoiCreateRequest.HoursRequest();
        hours.setDayOfWeek((short) 1);
        request.setHours(List.of(hours));

        PoiCreateRequest.MediaRequest media = new PoiCreateRequest.MediaRequest();
        media.setUrl("https://example.com/photo.jpg");
        media.setMediaType(PoiMedia.MediaType.PHOTO);
        request.setMedia(List.of(media));

        PoiCreateRequest.SourceRequest source = new PoiCreateRequest.SourceRequest();
        source.setSourceCode("WIKIPEDIA");
        source.setSourceUrl("https://ru.wikipedia.org/wiki/test");
        source.setExternalId("wiki-1");
        source.setConfidenceScore(new BigDecimal("0.85"));
        request.setSources(List.of(source));

        return request;
    }
}
