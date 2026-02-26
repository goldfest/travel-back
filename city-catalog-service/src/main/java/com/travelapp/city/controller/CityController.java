package com.travelapp.city.controller;

import com.travelapp.city.model.dto.request.CityRequestDto;
import com.travelapp.city.model.dto.response.CityLookupDto;
import com.travelapp.city.model.dto.response.CityResponseDto;
import com.travelapp.city.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
            @Parameter(description = "ID города", required = true)
            @PathVariable("id") Long id
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
            @Parameter(description = "ID города", required = true)
            @PathVariable("id") Long id
    ) {
        cityService.deleteCity(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Получить список городов",
            description = "Возвращает все города с пагинацией. " +
                    "Если передан параметр isPopular=true/false — возвращает только города по популярности."
    )
    @GetMapping
    public ResponseEntity<Page<CityResponseDto>> getCities(
            @Parameter(description = "Фильтр по популярности (true/false). Если не передан — все города", example = "true")
            @RequestParam(required = false) Boolean isPopular,

            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Размер страницы", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Поле сортировки (name, country, createdAt)", example = "name")
            @RequestParam(defaultValue = "name") String sort,

            @Parameter(description = "Направление сортировки (ASC/DESC)", example = "ASC")
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        Page<CityResponseDto> result = (isPopular == null)
                ? cityService.getAllCities(pageable)
                : cityService.getCitiesByPopularity(isPopular, pageable);

        return ResponseEntity.ok(result);
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
            @Parameter(description = "Поисковый запрос", example = "moscow")
            @RequestParam String query,

            @Parameter(description = "Номер страницы (0..N)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Размер страницы", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Поле сортировки (name, country, createdAt)", example = "name")
            @RequestParam(defaultValue = "name") String sort,

            @Parameter(description = "Направление сортировки", example = "ASC")
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        if (!List.of("name", "country", "createdAt").contains(sort)) {
            sort = "name";
        }

        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, Sort.by(direction, sort));
        Page<CityResponseDto> cities = cityService.searchCities(query, pageable);
        return ResponseEntity.ok(cities);
    }

    @Operation(
            summary = "Получить города по коду страны",
            description = "Возвращает города по ISO коду страны с пагинацией"
    )
    @GetMapping("/country/{countryCode}")
    public ResponseEntity<Page<CityResponseDto>> getCitiesByCountryCode(

            @Parameter(description = "Код страны (ISO 3166-1 alpha-2)", example = "TR")
            @PathVariable
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "Код страны должен состоять из 2 букв")
            String countryCode,

            @Parameter(description = "Номер страницы", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Размер страницы", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Поле сортировки (name, country, createdAt)", example = "name")
            @RequestParam(defaultValue = "name") String sort,

            @Parameter(description = "Направление сортировки (ASC/DESC)", example = "ASC")
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));
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

    @Operation(summary = "Получить города по списку ID", description = "Batch-эндпоинт для других сервисов")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Города найдены (возможны частичные совпадения)")
    })
    @PostMapping("/by-ids")
    public ResponseEntity<List<CityResponseDto>> getCitiesByIds(
            @RequestBody @NotEmpty(message = "Список id не должен быть пустым")
            @Schema(example = "[1,2,3]") List<Long> ids
    ) {
        List<CityResponseDto> cities = cityService.getCitiesByIds(ids);
        return ResponseEntity.ok(cities);
    }

    @Operation(summary = "Лёгкий список городов для выбора", description = "Для выпадающих списков/быстрых экранов")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список получен")
    })
    @GetMapping("/lookup")
    public ResponseEntity<List<CityLookupDto>> getLookupCities() {
        return ResponseEntity.ok(cityService.getLookupCities());
    }
}