package com.travelapp.route.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.config.YandexRoutingProperties;
import com.travelapp.route.model.dto.response.LatLngDto;
import com.travelapp.route.model.dto.routing.RoutingDayResult;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.RoutingSegmentResult;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.service.DistanceCalculationService;
import com.travelapp.route.service.RoutingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class YandexRoutingProvider implements RoutingProvider {

    private static final String PROVIDER = "YANDEX";
    private static final String SOURCE_ROADS = "ROADS";
    private static final String SOURCE_FALLBACK = "FALLBACK";

    private final DistanceCalculationService fallbackDistanceService;
    private final ObjectMapper objectMapper;
    private final YandexRoutingProperties properties;

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public RoutingDayResult buildDayRoute(List<RoutingPoint> points, Route.TransportMode transportMode) {
        RoutingDayResult result = new RoutingDayResult();

        if (points == null || points.size() < 2) {
            result.setSegments(List.of());
            result.setDayCoordinates(points == null ? List.of() : points.stream()
                    .map(p -> new LatLngDto(p.getLatitude(), p.getLongitude()))
                    .toList());
            result.setTotalDistanceKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            result.setTotalDurationMin(0);
            result.setProvider(PROVIDER);
            result.setGeometrySource(SOURCE_FALLBACK);
            return result;
        }

        List<RoutingSegmentResult> segments = new ArrayList<>();
        List<LatLngDto> merged = new ArrayList<>();

        BigDecimal totalDistance = BigDecimal.ZERO;
        int totalDuration = 0;
        boolean allSegmentsFromRoads = true;

        for (int i = 1; i < points.size(); i++) {
            RoutingPoint from = points.get(i - 1);
            RoutingPoint to = points.get(i);

            RoutingSegmentResult segment;
            try {
                segment = buildSegmentFromApi(from, to, transportMode);
            } catch (Exception e) {
                log.warn("Yandex routing failed for segment {} -> {}. Fallback will be used. Cause: {}",
                        from.getRoutePointId(), to.getRoutePointId(), e.getMessage());
                segment = buildSegmentFallback(from, to, transportMode);
                allSegmentsFromRoads = false;
            }

            if (!SOURCE_ROADS.equals(segment.getGeometrySource())) {
                allSegmentsFromRoads = false;
            }

            segments.add(segment);
            totalDistance = totalDistance.add(
                    segment.getDistanceKm() == null ? BigDecimal.ZERO : segment.getDistanceKm()
            );
            totalDuration += segment.getDurationMin() == null ? 0 : segment.getDurationMin();

            mergeCoordinates(merged, segment.getCoordinates());
        }

        result.setSegments(segments);
        result.setDayCoordinates(merged);
        result.setTotalDistanceKm(totalDistance.setScale(2, RoundingMode.HALF_UP));
        result.setTotalDurationMin(totalDuration);
        result.setProvider(PROVIDER);
        result.setGeometrySource(allSegmentsFromRoads ? SOURCE_ROADS : SOURCE_FALLBACK);

        return result;
    }

    @Override
    public TravelMatrixResult buildMatrix(List<RoutingPoint> points, Route.TransportMode transportMode) {
        if (points == null || points.isEmpty()) {
            TravelMatrixResult empty = new TravelMatrixResult();
            empty.setDistanceKm(new double[0][0]);
            empty.setDurationMin(new int[0][0]);
            return empty;
        }

        if (!isApiEnabled()) {
            return buildFallbackMatrix(points, transportMode);
        }

        try {
            return buildMatrixFromApi(points, transportMode);
        } catch (Exception e) {
            log.warn("Yandex distance matrix failed, using fallback. Cause: {}", e.getMessage());
            return buildFallbackMatrix(points, transportMode);
        }
    }

    private RoutingSegmentResult buildSegmentFromApi(
            RoutingPoint from,
            RoutingPoint to,
            Route.TransportMode transportMode
    ) throws IOException, InterruptedException {

        if (!isApiEnabled()) {
            return buildSegmentFallback(from, to, transportMode);
        }

        String mode = mapTransportMode(transportMode);
        String waypoints = formatPoint(from.getLatitude(), from.getLongitude()) +
                "|" +
                formatPoint(to.getLatitude(), to.getLongitude());

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getBaseUrl() + "/v2/route")
                .queryParam("apikey", properties.getApiKey())
                .queryParam("waypoints", waypoints)
                .queryParam("mode", mode)
                .queryParam("lang", properties.getLang());

        if ("driving".equals(mode)) {
            builder.queryParam("traffic", properties.getTraffic());
        }

        URI uri = builder.build().encode().toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Yandex route HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        ensureNoApiErrors(root);

        JsonNode legs = root.path("route").path("legs");
        if (!legs.isArray() || legs.isEmpty()) {
            throw new IOException("Yandex route response has no legs");
        }

        JsonNode leg = legs.get(0);
        String status = leg.path("status").asText("FAIL");
        if (!"OK".equalsIgnoreCase(status)) {
            throw new IOException("Yandex leg status is " + status);
        }

        JsonNode steps = leg.path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            throw new IOException("Yandex leg contains no steps");
        }

        double totalMeters = 0.0;
        double totalSeconds = 0.0;
        List<LatLngDto> coordinates = new ArrayList<>();

        for (JsonNode step : steps) {
            totalMeters += step.path("length").asDouble(0.0);
            totalSeconds += step.path("duration").asDouble(0.0);

            JsonNode pointsNode = step.path("polyline").path("points");
            if (!pointsNode.isArray()) {
                continue;
            }

            List<LatLngDto> stepCoordinates = parsePolylinePoints(pointsNode);
            mergeCoordinates(coordinates, stepCoordinates);
        }

        if (coordinates.size() < 2) {
            throw new IOException("Yandex route polyline is empty");
        }

        RoutingSegmentResult segment = new RoutingSegmentResult();
        segment.setFromRoutePointId(from.getRoutePointId());
        segment.setToRoutePointId(to.getRoutePointId());
        segment.setDistanceKm(toKm(totalMeters));
        segment.setDurationMin(toMinutes(totalSeconds));
        segment.setProvider(PROVIDER);
        segment.setGeometrySource(SOURCE_ROADS);
        segment.setStatus("OK");
        segment.setCoordinates(coordinates);

        return segment;
    }

    private TravelMatrixResult buildMatrixFromApi(
            List<RoutingPoint> points,
            Route.TransportMode transportMode
    ) throws IOException, InterruptedException {

        String mode = mapTransportMode(transportMode);
        String coords = points.stream()
                .map(p -> formatPoint(p.getLatitude(), p.getLongitude()))
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getBaseUrl() + "/v2/distancematrix")
                .queryParam("apikey", properties.getApiKey())
                .queryParam("origins", coords)
                .queryParam("destinations", coords)
                .queryParam("mode", mode);

        if ("driving".equals(mode)) {
            builder.queryParam("traffic", properties.getTraffic());
        }

        URI uri = builder.build().encode().toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Yandex matrix HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        ensureNoApiErrors(root);

        JsonNode rows = root.path("rows");
        if (!rows.isArray() || rows.size() != points.size()) {
            throw new IOException("Invalid matrix response size");
        }

        int n = points.size();
        double[][] distances = new double[n][n];
        int[][] durations = new int[n][n];

        for (int i = 0; i < n; i++) {
            JsonNode elements = rows.get(i).path("elements");
            if (!elements.isArray() || elements.size() != n) {
                throw new IOException("Invalid matrix elements size for row " + i);
            }

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distances[i][j] = 0.0;
                    durations[i][j] = 0;
                    continue;
                }

                JsonNode element = elements.get(j);
                String status = element.path("status").asText("FAIL");

                if ("OK".equalsIgnoreCase(status)) {
                    double meters = element.path("distance").path("value").asDouble(0.0);
                    double seconds = element.path("duration").path("value").asDouble(0.0);

                    distances[i][j] = roundKm(meters / 1000.0);
                    durations[i][j] = (int) Math.ceil(seconds / 60.0);
                } else {
                    double[] p1 = {points.get(i).getLatitude(), points.get(i).getLongitude()};
                    double[] p2 = {points.get(j).getLatitude(), points.get(j).getLongitude()};
                    double dist = fallbackDistanceService.calculateDistance(p1, p2);
                    int dur = fallbackDistanceService.calculateTravelTime(dist, transportMode.name());

                    distances[i][j] = roundKm(dist);
                    durations[i][j] = dur;
                }
            }
        }

        TravelMatrixResult result = new TravelMatrixResult();
        result.setDistanceKm(distances);
        result.setDurationMin(durations);
        return result;
    }

    private TravelMatrixResult buildFallbackMatrix(List<RoutingPoint> points, Route.TransportMode transportMode) {
        int n = points.size();
        double[][] distances = new double[n][n];
        int[][] durations = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distances[i][j] = 0.0;
                    durations[i][j] = 0;
                    continue;
                }

                double[] p1 = {points.get(i).getLatitude(), points.get(i).getLongitude()};
                double[] p2 = {points.get(j).getLatitude(), points.get(j).getLongitude()};
                double dist = fallbackDistanceService.calculateDistance(p1, p2);
                int dur = fallbackDistanceService.calculateTravelTime(dist, transportMode.name());

                distances[i][j] = roundKm(dist);
                durations[i][j] = dur;
            }
        }

        TravelMatrixResult result = new TravelMatrixResult();
        result.setDistanceKm(distances);
        result.setDurationMin(durations);
        return result;
    }

    private RoutingSegmentResult buildSegmentFallback(
            RoutingPoint from,
            RoutingPoint to,
            Route.TransportMode transportMode
    ) {
        double[] p1 = {from.getLatitude(), from.getLongitude()};
        double[] p2 = {to.getLatitude(), to.getLongitude()};
        double dist = fallbackDistanceService.calculateDistance(p1, p2);
        int dur = fallbackDistanceService.calculateTravelTime(dist, transportMode.name());

        RoutingSegmentResult segment = new RoutingSegmentResult();
        segment.setFromRoutePointId(from.getRoutePointId());
        segment.setToRoutePointId(to.getRoutePointId());
        segment.setDistanceKm(BigDecimal.valueOf(dist).setScale(2, RoundingMode.HALF_UP));
        segment.setDurationMin(dur);
        segment.setProvider(PROVIDER);
        segment.setGeometrySource(SOURCE_FALLBACK);
        segment.setStatus("OK");
        segment.setCoordinates(List.of(
                new LatLngDto(from.getLatitude(), from.getLongitude()),
                new LatLngDto(to.getLatitude(), to.getLongitude())
        ));
        return segment;
    }

    private boolean isApiEnabled() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    private String mapTransportMode(Route.TransportMode transportMode) {
        return switch (transportMode) {
            case WALK -> "walking";
            case CAR -> "driving";
            case PUBLIC_TRANSPORT -> "transit";
            case MIXED -> "transit";
        };
    }

    private String formatPoint(double latitude, double longitude) {
        return latitude + "," + longitude;
    }

    private void ensureNoApiErrors(JsonNode root) throws IOException {
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            List<String> messages = new ArrayList<>();
            errors.forEach(node -> messages.add(node.asText()));
            throw new IOException(String.join("; ", messages));
        }
    }

    private List<LatLngDto> parsePolylinePoints(JsonNode pointsNode) {
        List<LatLngDto> result = new ArrayList<>();

        for (JsonNode pointNode : pointsNode) {
            if (!pointNode.isArray() || pointNode.size() < 2) {
                continue;
            }

            double lat = pointNode.get(0).asDouble();
            double lon = pointNode.get(1).asDouble();
            result.add(new LatLngDto(lat, lon));
        }

        return result;
    }

    private void mergeCoordinates(List<LatLngDto> target, List<LatLngDto> source) {
        if (source == null || source.isEmpty()) {
            return;
        }

        if (target.isEmpty()) {
            target.addAll(source);
            return;
        }

        LatLngDto last = target.get(target.size() - 1);
        int startIndex = 0;

        if (samePoint(last, source.get(0))) {
            startIndex = 1;
        }

        for (int i = startIndex; i < source.size(); i++) {
            target.add(source.get(i));
        }
    }

    private boolean samePoint(LatLngDto a, LatLngDto b) {
        if (a == null || b == null) return false;

        double eps = 1e-7;
        return Math.abs(a.getLatitude() - b.getLatitude()) < eps
                && Math.abs(a.getLongitude() - b.getLongitude()) < eps;
    }

    private BigDecimal toKm(double meters) {
        return BigDecimal.valueOf(meters / 1000.0).setScale(2, RoundingMode.HALF_UP);
    }

    private int toMinutes(double seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }

    private double roundKm(double km) {
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}