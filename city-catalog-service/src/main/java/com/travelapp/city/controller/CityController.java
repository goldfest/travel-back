package com.travelapp.city.controller;

import com.travelapp.city.model.dto.request.CityRequestDto;
import com.travelapp.city.model.dto.response.CityResponseDto;
import com.travelapp.city.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "City Management", description = "API для управления городами")
public class CityController {

    private final CityService cityService;

    @Operation(summary = "Создать новый город", description = "Создание нового города в системе")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Город успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "409", description = "Город с таким slug уже существует")
    })
    @PostMapping
    public ResponseEntity<CityResponseDto> createCity(@Valid @RequestBody CityRequestDto requestDto) {
        CityResponseDto city = cityService.createCity(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(city);
    }

    @Operation(summary = "Обновить город", description = "Обновление информации о городе")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Город успешно обновлен"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "404", description = "Город не найден"),
            @ApiResponse(responseCode = "409", description = "Город с таким slug уже существует")
    })
    @PutMapping("/{id}")
    public ResponseEntity<CityResponseDto> updateCity(
            @Parameter(description = "ID города") @PathVariable Long id,
            @Valid @RequestBody CityRequestDto requestDto
    ) {
        CityResponseDto city = cityService.updateCity(id, requestDto);
        return ResponseEntity.ok(city);
    }

    @Operation(summary = "Получить город по ID", description = "Получение информации о городе по его идентификатору")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Город найден"),
            @ApiResponse(responseCode = "404", description = "Город не найден")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CityResponseDto> getCityById(
            @Parameter(description = "ID города") @PathVariable Long id
    ) {
        CityResponseDto city = cityService.getCityById(id);
        return ResponseEntity.ok(city);
    }

    @Operation(summary = "Получить город по slug", description = "Получение информации о городе по его URL-идентификатору")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Город найден"),
            @ApiResponse(responseCode = "404", description = "Город не найден")
    })
    @GetMapping("/slug/{slug}")
    public ResponseEntity<CityResponseDto> getCityBySlug(
            @Parameter(description = "Slug города") @PathVariable String slug
    ) {
        CityResponseDto city = cityService.getCityBySlug(slug);
        return ResponseEntity.ok(city);
    }

    @Operation(summary = "Удалить город", description = "Удаление города из системы")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Город успешно удален"),
            @ApiResponse(responseCode = "404", description = "Город не найден")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCity(
            @Parameter(description = "ID города") @PathVariable Long id
    ) {
        cityService.deleteCity(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Получить все города", description = "Получение списка всех городов с пагинацией")
    @GetMapping
    public ResponseEntity<Page<CityResponseDto>> getAllCities(
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<CityResponseDto> cities = cityService.getAllCities(pageable);
        return ResponseEntity.ok(cities);
    }

    @Operation(summary = "Получить популярные города", description = "Получение списка городов, отмеченных как популярные")
    @GetMapping("/popular")
    public ResponseEntity<List<CityResponseDto>> getPopularCities() {
        List<CityResponseDto> cities = cityService.getPopularCities();
        return ResponseEntity.ok(cities);
    }

    @Operation(summary = "Поиск городов", description = "Поиск городов по названию, стране или описанию")
    @GetMapping("/search")
    public ResponseEntity<Page<CityResponseDto>> searchCities(
            @Parameter(description = "Поисковый запрос") @RequestParam String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<CityResponseDto> cities = cityService.searchCities(query, pageable);
        return ResponseEntity.ok(cities);
    }

    @Operation(summary = "Получить города по коду страны", description = "Получение списка городов по ISO коду страны")
    @GetMapping("/country/{countryCode}")
    public ResponseEntity<Page<CityResponseDto>> getCitiesByCountryCode(
            @Parameter(description = "Код страны (ISO 3166-1 alpha-2)")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "Код страны должен состоять из 2 букв")
            String countryCode,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<CityResponseDto> cities = cityService.getCitiesByCountryCode(countryCode, pageable);
        return ResponseEntity.ok(cities);
    }

    @Operation(summary = "Проверить существование города по slug", description = "Проверка, существует ли город с указанным slug")
    @GetMapping("/exists/{slug}")
    public ResponseEntity<Boolean> checkCityExists(
            @Parameter(description = "Slug города") @PathVariable String slug
    ) {
        boolean exists = cityService.existsBySlug(slug);
        return ResponseEntity.ok(exists);
    }
}