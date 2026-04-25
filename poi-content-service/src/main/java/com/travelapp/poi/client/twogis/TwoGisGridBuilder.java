package com.travelapp.poi.client.twogis;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TwoGisGridBuilder {

    public List<SearchPoint> buildGridPoints(BigDecimal centerLat,
                                             BigDecimal centerLng,
                                             int gridRadiusKm,
                                             int cellStepKm) {
        List<SearchPoint> points = new ArrayList<>();

        double centerLatVal = centerLat.doubleValue();
        double centerLngVal = centerLng.doubleValue();

        double latStep = kmToLatitudeDegrees(cellStepKm);
        double lngStep = kmToLongitudeDegrees(cellStepKm, centerLatVal);

        int steps = Math.max(1, gridRadiusKm / Math.max(cellStepKm, 1));

        for (int latIndex = -steps; latIndex <= steps; latIndex++) {
            for (int lngIndex = -steps; lngIndex <= steps; lngIndex++) {
                double lat = centerLatVal + latIndex * latStep;
                double lng = centerLngVal + lngIndex * lngStep;

                double distanceKm = approximateDistanceKm(
                        centerLatVal,
                        centerLngVal,
                        lat,
                        lng
                );

                points.add(new SearchPoint(
                        roundCoord(lat),
                        roundCoord(lng),
                        roundCoord(distanceKm)
                ));
            }
        }

        points.sort(Comparator.comparingDouble(SearchPoint::distanceKm));
        return points;
    }

    private double kmToLatitudeDegrees(double km) {
        return km / 111.0;
    }

    private double kmToLongitudeDegrees(double km, double lat) {
        double cos = Math.cos(Math.toRadians(lat));
        if (Math.abs(cos) < 0.0001) {
            cos = 0.0001;
        }
        return km / (111.0 * cos);
    }

    private double approximateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        double latDiffKm = (lat2 - lat1) * 111.0;
        double lngDiffKm = (lng2 - lng1) * 111.0 * Math.cos(Math.toRadians((lat1 + lat2) / 2.0));
        return Math.sqrt(latDiffKm * latDiffKm + lngDiffKm * lngDiffKm);
    }

    private double roundCoord(double value) {
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
