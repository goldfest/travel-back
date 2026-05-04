package com.travelapp.city.validation;

import com.travelapp.city.model.dto.request.CityRequestDto;
import jakarta.validation.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CityRequestDtoValidationTest {

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
    void validate_shouldHaveNoViolations_whenRequestIsCorrect() {
        CityRequestDto request = validRequest();

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_shouldReturnViolation_whenNameIsBlank() {
        CityRequestDto request = validRequest();
        request.setName("   ");

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("name");
                    assertThat(violation.getMessage()).contains("Название города обязательно");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenSlugHasInvalidFormat() {
        CityRequestDto request = validRequest();
        request.setSlug("Ulyanovsk City");

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("slug");
                    assertThat(violation.getMessage()).contains("Slug должен быть в нижнем регистре");
                });
    }

    @Test
    void validate_shouldReturnViolation_whenCountryCodeHasMoreThanTwoCharacters() {
        CityRequestDto request = validRequest();
        request.setCountryCode("RUS");

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("countryCode")
                );
    }

    @Test
    void validate_shouldReturnViolation_whenLatitudeIsOutOfRange() {
        CityRequestDto request = validRequest();
        request.setCenterLat(new BigDecimal("91.000000"));

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("centerLat")
                );
    }

    @Test
    void validate_shouldReturnViolation_whenLongitudeIsOutOfRange() {
        CityRequestDto request = validRequest();
        request.setCenterLng(new BigDecimal("181.000000"));

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("centerLng")
                );
    }

    @Test
    void validate_shouldReturnViolation_whenImageUrlIsTooLong() {
        CityRequestDto request = validRequest();
        request.setImageUrl("https://example.com/" + "a".repeat(501));

        Set<ConstraintViolation<CityRequestDto>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("imageUrl")
                );
    }

    private CityRequestDto validRequest() {
        CityRequestDto dto = new CityRequestDto();
        dto.setName("Ульяновск");
        dto.setCountry("Россия");
        dto.setDescription("Город на Волге");
        dto.setCenterLat(new BigDecimal("54.3142"));
        dto.setCenterLng(new BigDecimal("48.4031"));
        dto.setIsPopular(true);
        dto.setSlug("ulyanovsk");
        dto.setCountryCode("RU");
        dto.setTimeZone("Europe/Samara");
        dto.setImageUrl("https://example.com/ulyanovsk.jpg");
        dto.setImageUrls(List.of("https://example.com/ulyanovsk-1.jpg"));
        return dto;
    }
}