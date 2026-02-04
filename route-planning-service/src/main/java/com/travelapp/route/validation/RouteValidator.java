package com.travelapp.route.validation;

import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteUpdateRequest;
import com.travelapp.route.model.entity.Route;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
@Slf4j
public class RouteValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return RouteCreateRequest.class.equals(clazz) ||
                RouteUpdateRequest.class.equals(clazz) ||
                Route.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (target instanceof RouteCreateRequest) {
            validateCreateRequest((RouteCreateRequest) target, errors);
        } else if (target instanceof RouteUpdateRequest) {
            validateUpdateRequest((RouteUpdateRequest) target, errors);
        } else if (target instanceof Route) {
            validateRoute((Route) target, errors);
        }
    }

    private void validateCreateRequest(RouteCreateRequest request, Errors errors) {
        if (request.getName() != null && request.getName().length() > 255) {
            errors.rejectValue("name", "name.length",
                    "Название маршрута не должно превышать 255 символов");
        }

        if (request.getDescription() != null && request.getDescription().length() > 500) {
            errors.rejectValue("description", "description.length",
                    "Описание не должно превышать 500 символов");
        }

        if (request.getCityId() == null) {
            errors.rejectValue("cityId", "cityId.required",
                    "ID города обязателен");
        }

        if (request.getDaysCount() != null && (request.getDaysCount() < 1 || request.getDaysCount() > 30)) {
            errors.rejectValue("daysCount", "daysCount.range",
                    "Количество дней должно быть от 1 до 30");
        }
    }

    private void validateUpdateRequest(RouteUpdateRequest request, Errors errors) {
        if (request.getName() != null && request.getName().length() > 255) {
            errors.rejectValue("name", "name.length",
                    "Название маршрута не должно превышать 255 символов");
        }

        if (request.getDescription() != null && request.getDescription().length() > 500) {
            errors.rejectValue("description", "description.length",
                    "Описание не должно превышать 500 символов");
        }

        if (request.getIsArchived() != null &&
                !request.getIsArchived().equals((short) 0) &&
                !request.getIsArchived().equals((short) 1)) {
            errors.rejectValue("isArchived", "isArchived.invalid",
                    "Флаг архивации должен быть 0 или 1");
        }

        if (request.getDistanceKm() != null && request.getDistanceKm() < 0) {
            errors.rejectValue("distanceKm", "distanceKm.positive",
                    "Расстояние должно быть положительным");
        }

        if (request.getDurationMin() != null && request.getDurationMin() < 0) {
            errors.rejectValue("durationMin", "durationMin.positive",
                    "Продолжительность должна быть положительной");
        }
    }

    private void validateRoute(Route route, Errors errors) {
        if (route.getName() == null || route.getName().trim().isEmpty()) {
            errors.rejectValue("name", "name.required",
                    "Название маршрута обязательно");
        }

        if (route.getUserId() == null) {
            errors.rejectValue("userId", "userId.required",
                    "ID пользователя обязателен");
        }

        if (route.getCityId() == null) {
            errors.rejectValue("cityId", "cityId.required",
                    "ID города обязателен");
        }

        if (route.getTransportMode() == null) {
            errors.rejectValue("transportMode", "transportMode.required",
                    "Режим транспорта обязателен");
        }

        // Валидация дней маршрута
        if (route.getRouteDays() != null) {
            for (int i = 0; i < route.getRouteDays().size(); i++) {
                var day = route.getRouteDays().get(i);
                if (day.getDayNumber() == null || day.getDayNumber() < 1) {
                    errors.rejectValue("routeDays[" + i + "].dayNumber",
                            "dayNumber.invalid", "Номер дня должен быть положительным");
                }

                if (day.getPlannedStart() != null && day.getPlannedEnd() != null &&
                        day.getPlannedStart().isAfter(day.getPlannedEnd())) {
                    errors.rejectValue("routeDays[" + i + "].plannedStart",
                            "plannedStart.afterEnd",
                            "Время начала должно быть раньше времени окончания");
                }
            }
        }
    }

    public static class RouteNameValidator implements jakarta.validation.ConstraintValidator<ValidRouteName, String> {

        @Override
        public void initialize(ValidRouteName constraintAnnotation) {
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }

            // Проверка длины
            if (value.length() > 255) {
                return false;
            }

            // Запрещенные слова/символы
            String[] forbiddenWords = {"админ", "admin", "система", "system", "тест", "test"};
            String lowerValue = value.toLowerCase();

            for (String word : forbiddenWords) {
                if (lowerValue.contains(word)) {
                    return false;
                }
            }

            // Проверка на специальные символы (разрешены только основные)
            return value.matches("^[a-zA-Zа-яА-Я0-9\\s\\-_,.()!?'\"]+$");
        }
    }
}