package com.travelapp.route.service.impl;

import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RouteDayPath;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteDayRepository;
import com.travelapp.route.repository.RouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteGraphFinalizeServiceTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private RouteDayRepository routeDayRepository;

    @Mock
    private RouteDayPathRepository routeDayPathRepository;

    @InjectMocks
    private RouteGraphFinalizeService service;

    @Test
    void finalizePreparedRoute_shouldRecalculateMetricsAndMoveRouteToReady() {
        Route route = route(100L, Route.RouteStatus.GRAPH_PREPARING);
        RouteDay day = day(11L, (short) 1, LocalDateTime.of(2026, 4, 15, 9, 0));
        day.addRoutePoint(point(1L, (short) 1, "Музей", 45));
        day.addRoutePoint(point(2L, (short) 2, "Парк", 30));
        RouteDayPath path = new RouteDayPath();
        path.setRouteDay(day);
        path.setDistanceKm(new BigDecimal("2.50"));
        path.setDurationMin(20);

        when(routeRepository.findWithDaysById(100L)).thenReturn(Optional.of(route));
        when(routeDayRepository.findWithPointsByRouteId(100L)).thenReturn(List.of(day));
        when(routeDayPathRepository.findByRouteDayId(11L)).thenReturn(Optional.of(path));

        service.finalizePreparedRoute(100L);

        ArgumentCaptor<Route> captor = ArgumentCaptor.forClass(Route.class);
        verify(routeRepository).save(captor.capture());
        Route saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Route.RouteStatus.READY);
        assertThat(saved.getDistanceKm()).isEqualByComparingTo("2.50");
        assertThat(saved.getDurationMin()).isEqualTo(95);
        assertThat(saved.getStartPoint()).isEqualTo("Музей");
        assertThat(saved.getEndPoint()).isEqualTo("Парк");
        assertThat(day.getRoutePoints().get(0).getPlannedArrivalAt()).isEqualTo(day.getPlannedStart());
        assertThat(day.getRoutePoints().get(0).getPlannedDepartureAt()).isEqualTo(day.getPlannedStart().plusMinutes(45));
    }

    @Test
    void markRoutePreparationFailed_shouldChangeStatusToDraft_whenRouteIsPreparing() {
        Route route = route(100L, Route.RouteStatus.GRAPH_PREPARING);
        when(routeRepository.findById(100L)).thenReturn(Optional.of(route));

        service.markRoutePreparationFailed(100L);

        assertThat(route.getStatus()).isEqualTo(Route.RouteStatus.DRAFT);
        verify(routeRepository).save(route);
    }

    @Test
    void markRoutePreparationFailed_shouldNotSave_whenRouteIsNotPreparing() {
        Route route = route(100L, Route.RouteStatus.READY);
        when(routeRepository.findById(100L)).thenReturn(Optional.of(route));

        service.markRoutePreparationFailed(100L);

        assertThat(route.getStatus()).isEqualTo(Route.RouteStatus.READY);
        verify(routeRepository, never()).save(any(Route.class));
    }

    private Route route(Long id, Route.RouteStatus status) {
        Route route = new Route();
        route.setId(id);
        route.setUserId(5L);
        route.setCityId(10L);
        route.setStatus(status);
        return route;
    }

    private RouteDay day(Long id, Short dayNumber, LocalDateTime plannedStart) {
        RouteDay day = new RouteDay();
        day.setId(id);
        day.setDayNumber(dayNumber);
        day.setPlannedStart(plannedStart);
        return day;
    }

    private RoutePoint point(Long id, Short orderIndex, String name, Integer visitMinutes) {
        RoutePoint point = new RoutePoint();
        point.setId(id);
        point.setOrderIndex(orderIndex);
        point.setPoiId(id + 100);
        point.setPoiName(name);
        point.setEstimatedVisitMinutes(visitMinutes);
        return point;
    }
}
