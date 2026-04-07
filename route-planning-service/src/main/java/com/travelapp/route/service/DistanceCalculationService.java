package com.travelapp.route.service;

import com.github.davidmoten.geo.GeoHash;
import com.github.davidmoten.geo.LatLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DistanceCalculationService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Рассчитывает расстояние между двумя точками по формуле гаверсинусов
     */
    public double calculateDistance(double[] point1, double[] point2) {
        if (point1 == null || point2 == null || point1.length < 2 || point2.length < 2) {
            return 0.0;
        }

        double lat1 = Math.toRadians(point1[0]);
        double lon1 = Math.toRadians(point1[1]);
        double lat2 = Math.toRadians(point2[0]);
        double lon2 = Math.toRadians(point2[1]);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Рассчитывает примерное время перемещения между точками
     * @param distanceKm расстояние в километрах
     * @param transportMode режим транспорта
     * @return время в минутах
     */
    public int calculateTravelTime(double distanceKm, String transportMode) {
        double speedKmH;

        switch (transportMode.toUpperCase()) {
            case "WALK":
                speedKmH = 5.0; // 5 км/ч - средняя скорость пешком
                break;
            case "CAR":
                speedKmH = 40.0; // 40 км/ч - средняя скорость в городе
                break;
            case "PUBLIC_TRANSPORT":
                speedKmH = 25.0; // 25 км/ч - средняя скорость общественного транспорта
                break;
            case "MIXED":
                speedKmH = 15.0; // 15 км/ч - смешанный режим
                break;
            default:
                speedKmH = 5.0;
        }

        // Время = расстояние / скорость (в часах), переводим в минуты
        double timeHours = distanceKm / speedKmH;
        return (int) Math.ceil(timeHours * 60);
    }

    /**
     * Рассчитывает расстояние и время между точками
     */
    public TravelInfo calculateTravelInfo(double[] point1, double[] point2, String transportMode) {
        double distance = calculateDistance(point1, point2);
        int time = calculateTravelTime(distance, transportMode);

        return new TravelInfo(distance, time);
    }

    /**
     * Находит ближайшую точку из списка
     */
    public NearestPoint findNearestPoint(double[] referencePoint, List<double[]> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        int nearestIndex = -1;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            double distance = calculateDistance(referencePoint, points.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }

        return new NearestPoint(nearestIndex, minDistance);
    }

    /**
     * Генерирует геохэш для точки
     */
    public String generateGeoHash(double latitude, double longitude, int precision) {
        return GeoHash.encodeHash(latitude, longitude, precision);
    }

    /**
     * Декодирует геохэш в координаты
     */
    public LatLong decodeGeoHash(String geoHash) {
        return GeoHash.decodeHash(geoHash);
    }

    /**
     * Проверяет, находятся ли точки в пределах заданного радиуса
     */
    public boolean isWithinRadius(double[] point1, double[] point2, double radiusKm) {
        double distance = calculateDistance(point1, point2);
        return distance <= radiusKm;
    }

    /**
     * Рассчитывает общее расстояние для маршрута из нескольких точек
     */
    public double calculateTotalDistance(List<double[]> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;

        for (int i = 1; i < points.size(); i++) {
            totalDistance += calculateDistance(points.get(i - 1), points.get(i));
        }

        return totalDistance;
    }

    /**
     * Рассчитывает общее время для маршрута
     */
    public int calculateTotalTravelTime(List<double[]> points, String transportMode) {
        double totalDistance = calculateTotalDistance(points);
        return calculateTravelTime(totalDistance, transportMode);
    }

    // Вспомогательные классы для возврата комплексных результатов
    public static class TravelInfo {
        private final double distanceKm;
        private final int timeMinutes;

        public TravelInfo(double distanceKm, int timeMinutes) {
            this.distanceKm = Math.round(distanceKm * 100.0) / 100.0;
            this.timeMinutes = timeMinutes;
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public int getTimeMinutes() {
            return timeMinutes;
        }
    }

    public static class NearestPoint {
        private final int index;
        private final double distanceKm;

        public NearestPoint(int index, double distanceKm) {
            this.index = index;
            this.distanceKm = Math.round(distanceKm * 100.0) / 100.0;
        }

        public int getIndex() {
            return index;
        }

        public double getDistanceKm() {
            return distanceKm;
        }
    }
}