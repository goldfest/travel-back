package com.travelapp.route.service.impl;

import com.travelapp.route.client.NotificationClient;
import com.travelapp.route.model.dto.notification.CreateNotificationRequest;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.service.RouteNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteNotificationServiceImpl implements RouteNotificationService {

    private static final String CHANNEL_IN_APP = "IN_APP";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final NotificationClient notificationClient;

    @Override
    public void notifyRouteCreated(Route route) {
        try {
            notificationClient.createNotification(CreateNotificationRequest.builder()
                    .type("route_created")
                    .title("Маршрут создан")
                    .description("Маршрут «" + route.getName() + "» успешно создан.")
                    .routeId(route.getId())
                    .userId(route.getUserId())
                    .eventKey("route_created:route:" + route.getId())
                    .deliveryChannel(CHANNEL_IN_APP)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to create route_created notification for route {}: {}", route.getId(), e.getMessage());
        }
    }

    @Override
    public void rescheduleOptimizedRouteNotifications(Route route) {
        try {
            notificationClient.deleteRouteScheduledNotifications(route.getId());
        } catch (Exception e) {
            log.warn("Failed to clear old route notifications for route {}: {}", route.getId(), e.getMessage());
        }

        List<CreateNotificationRequest> requests = new ArrayList<>();
        route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .forEach(day -> buildDayNotifications(route, day).ifPresent(requests::addAll));

        if (requests.isEmpty()) {
            return;
        }

        try {
            notificationClient.createBatchNotifications(requests);
        } catch (Exception e) {
            log.warn("Failed to schedule route notifications for route {}: {}", route.getId(), e.getMessage());
        }
    }

    @Override
    public void deleteRouteNotifications(Route route) {
        try {
            notificationClient.deleteRouteScheduledNotifications(route.getId());
        } catch (Exception e) {
            log.warn("Failed to delete route notifications for route {}: {}", route.getId(), e.getMessage());
        }
    }

    private java.util.Optional<List<CreateNotificationRequest>> buildDayNotifications(Route route, RouteDay day) {
        LocalDateTime dayStart = day.getPlannedStart();
        if (dayStart == null) {
            return java.util.Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        List<CreateNotificationRequest> requests = new ArrayList<>();

        String formatted = dayStart.format(DATE_FORMATTER);

        if (dayStart.isAfter(now)) {
            CreateNotificationRequest startNotification = CreateNotificationRequest.builder()
                    .type("route_day_start")
                    .title("Начинается день маршрута")
                    .description("Маршрут «" + route.getName() + "», день " + day.getDayNumber() + ", начинается " + formatted + ".")
                    .scheduledAt(dayStart)
                    .routeId(route.getId())
                    .routeDayId(day.getId())
                    .userId(route.getUserId())
                    .eventKey("route_day_start:route:" + route.getId() + ":day:" + day.getId())
                    .deliveryChannel(CHANNEL_IN_APP)
                    .build();

            requests.add(startNotification);
        }

        LocalDateTime reminderAt = dayStart.minusHours(2);
        if (reminderAt.isAfter(now)) {
            CreateNotificationRequest reminderNotification = CreateNotificationRequest.builder()
                    .type("route_reminder")
                    .title("Напоминание о маршруте")
                    .description("Через 2 часа начинается маршрут «" + route.getName() + "», день " + day.getDayNumber() + ".")
                    .scheduledAt(reminderAt)
                    .routeId(route.getId())
                    .routeDayId(day.getId())
                    .userId(route.getUserId())
                    .eventKey("route_reminder:route:" + route.getId() + ":day:" + day.getId() + ":minus120")
                    .deliveryChannel(CHANNEL_IN_APP)
                    .build();

            requests.add(reminderNotification);
        }

        return requests.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(requests);
    }
}
