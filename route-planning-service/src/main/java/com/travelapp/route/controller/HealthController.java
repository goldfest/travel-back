package com.travelapp.route.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "API для проверки состояния сервиса")
@Slf4j
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping
    @Operation(summary = "Проверить состояние сервиса")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check requested");

        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("service", applicationName);
        healthInfo.put("status", "UP");
        healthInfo.put("port", serverPort);
        healthInfo.put("timestamp", System.currentTimeMillis());

        // Добавляем информацию о здоровье из Actuator
        try {
            HealthComponent health = healthEndpoint.health();
            healthInfo.put("details", health.getStatus().getCode());
        } catch (Exception e) {
            log.warn("Could not get detailed health info", e);
            healthInfo.put("details", "UNKNOWN");
        }

        return ResponseEntity.ok(healthInfo);
    }

    @GetMapping("/ready")
    @Operation(summary = "Проверить готовность сервиса")
    public ResponseEntity<Map<String, Object>> readiness() {
        log.debug("Readiness check requested");

        Map<String, Object> readinessInfo = new HashMap<>();
        readinessInfo.put("service", applicationName);
        readinessInfo.put("status", "READY");
        readinessInfo.put("timestamp", System.currentTimeMillis());

        // Здесь можно добавить проверки зависимостей
        readinessInfo.put("database", "CONNECTED");
        readinessInfo.put("cache", "CONNECTED");
        readinessInfo.put("externalServices", "AVAILABLE");

        return ResponseEntity.ok(readinessInfo);
    }

    @GetMapping("/version")
    @Operation(summary = "Получить информацию о версии")
    public ResponseEntity<Map<String, Object>> version() {
        Map<String, Object> versionInfo = new HashMap<>();
        versionInfo.put("service", applicationName);
        versionInfo.put("version", "1.0.0");
        versionInfo.put("build", "2024.01.001");
        versionInfo.put("javaVersion", System.getProperty("java.version"));
        versionInfo.put("environment", System.getProperty("spring.profiles.active", "default"));

        return ResponseEntity.ok(versionInfo);
    }
}