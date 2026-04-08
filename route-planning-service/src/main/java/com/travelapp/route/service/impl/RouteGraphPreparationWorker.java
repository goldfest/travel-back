package com.travelapp.route.service.impl;

import com.travelapp.route.client.GraphImportClient;
import com.travelapp.route.model.dto.internal.GraphImportTriggerRequest;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.RoutePathCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteGraphPreparationWorker {

    private static final Duration GRAPH_WAIT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration GRAPH_WAIT_POLL_INTERVAL = Duration.ofSeconds(2);

    private final GraphImportClient graphImportClient;
    private final GraphVersionService graphVersionService;
    private final RoutePathCacheService routePathCacheService;
    private final RouteGraphFinalizeService routeGraphFinalizeService;

    @Async
    public void prepareGraphAndRoute(Long routeId, Long cityId) {
        try {
            ensureActiveGraphReady(cityId);

            routePathCacheService.rebuildRoutePaths(routeId);

            routeGraphFinalizeService.finalizePreparedRoute(routeId);
            log.info("Route graph preparation finished successfully for routeId={}, cityId={}", routeId, cityId);
        } catch (Exception ex) {
            log.error("Route graph preparation failed for routeId={}, cityId={}", routeId, cityId, ex);
            routeGraphFinalizeService.markRoutePreparationFailed(routeId);
        }
    }

    private void ensureActiveGraphReady(Long cityId) {
        if (graphVersionService.hasActiveVersion(cityId)) {
            return;
        }

        log.info("No active graph for city {}, starting async graph import", cityId);
        graphImportClient.importCityGraph(new GraphImportTriggerRequest(cityId, null));

        long deadline = System.currentTimeMillis() + GRAPH_WAIT_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (graphVersionService.hasActiveVersion(cityId)) {
                log.info("Active graph became available for cityId={}", cityId);
                return;
            }

            try {
                Thread.sleep(GRAPH_WAIT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ожидание подготовки графа было прервано", interruptedException);
            }
        }

        throw new IllegalStateException("Не удалось дождаться активной версии графа для города " + cityId);
    }
}