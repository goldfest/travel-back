package com.travelapp.graphimport.validation;

import com.travelapp.graphimport.model.dto.GraphImportRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraphImportRequestValidationTest {

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
    void validate_shouldPass_whenCityIdIsPresent() {
        GraphImportRequest request = new GraphImportRequest();
        request.setCityId(10L);
        request.setOsmFilePath("ulyanovsk.osm");

        Set<ConstraintViolation<GraphImportRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_shouldReturnViolation_whenCityIdIsNull() {
        GraphImportRequest request = new GraphImportRequest();

        Set<ConstraintViolation<GraphImportRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("cityId"));
    }
}
