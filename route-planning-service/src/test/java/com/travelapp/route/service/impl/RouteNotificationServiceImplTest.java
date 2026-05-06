package com.travelapp.route.service.impl;

import com.travelapp.route.client.NotificationClient;
import com.travelapp.route.model.dto.notification.CreateNotificationRequest;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteNotificationServiceImplTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private RouteNotificationServiceImpl service;

    @Test
    void notifyRouteCreated_shouldSendInAppNotification() {
        Route route = route(100L, 5L, "Маршрут по Ульяновску");
        when(notificationClient.createNotification(any(CreateNotificationRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        service.notifyRouteCreated(route);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationClient).createNotification(captor.capture());
        CreateNotificationRequest request = captor.getValue();
        assertThat(request.getType()).isEqualTo("route_created");
        assertThat(request.getTitle()).isEqualTo("Маршрут создан");
        assertThat(request.getRouteId()).isEqualTo(100L);
        assertThat(request.getUserId()).isEqualTo(5L);
        assertThat(request.getEventKey()).isEqualTo("route_created:route:100");
        assertThat(request.getDeliveryChannel()).isEqualTo("IN_APP");
    }

    @Test
    void notifyRouteCreated_shouldNotThrow_whenNotificationClientFails() {
        Route route = route(100L, 5L, "Маршрут");
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationClient).createNotification(any(CreateNotificationRequest.class));

        service.notifyRouteCreated(route);

        verify(notificationClient).createNotification(any(CreateNotificationRequest.class));
    }

    @Test
    void rescheduleOptimizedRouteNotifications_shouldDeleteOldAndCreateBatchForFutureDay() {
        Route route = route(100L, 5L, "Маршрут");
        RouteDay day = day(11L, (short) 1, LocalDateTime.now().plusDays(1));
        route.addRouteDay(day);

        service.rescheduleOptimizedRouteNotifications(route);

        verify(notificationClient).deleteRouteScheduledNotifications(100L);
        ArgumentCaptor<List<CreateNotificationRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationClient).createBatchNotifications(captor.capture());

        List<CreateNotificationRequest> requests = captor.getValue();
        assertThat(requests).hasSize(2);
        assertThat(requests).extracting(CreateNotificationRequest::getType)
                .containsExactly("route_day_start", "route_reminder");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.getRouteId()).isEqualTo(100L);
            assertThat(request.getRouteDayId()).isEqualTo(11L);
            assertThat(request.getUserId()).isEqualTo(5L);
            assertThat(request.getDeliveryChannel()).isEqualTo("IN_APP");
        });
    }

    @Test
    void rescheduleOptimizedRouteNotifications_shouldNotCreateBatch_whenDayHasNoPlannedStart() {
        Route route = route(100L, 5L, "Маршрут");
        route.addRouteDay(day(11L, (short) 1, null));

        service.rescheduleOptimizedRouteNotifications(route);

        verify(notificationClient).deleteRouteScheduledNotifications(100L);
        verify(notificationClient, never()).createBatchNotifications(anyList());
    }

    @Test
    void deleteRouteNotifications_shouldCallNotificationClient() {
        Route route = route(100L, 5L, "Маршрут");

        service.deleteRouteNotifications(route);

        verify(notificationClient).deleteRouteScheduledNotifications(100L);
    }

    private Route route(Long id, Long userId, String name) {
        Route route = new Route();
        route.setId(id);
        route.setUserId(userId);
        route.setName(name);
        route.setCityId(10L);
        return route;
    }

    private RouteDay day(Long id, Short dayNumber, LocalDateTime plannedStart) {
        RouteDay day = new RouteDay();
        day.setId(id);
        day.setDayNumber(dayNumber);
        day.setPlannedStart(plannedStart);
        return day;
    }
}
