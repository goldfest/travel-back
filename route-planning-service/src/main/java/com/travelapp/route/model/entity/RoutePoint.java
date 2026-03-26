package com.travelapp.route.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoutePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_index", nullable = false)
    private Short orderIndex;

    @Column(name = "poi_id", nullable = false)
    private Long poiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_day_id", nullable = false)
    private RouteDay routeDay;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "poi_name", length = 255)
    private String poiName;

    @Column(name = "poi_address", length = 500)
    private String poiAddress;

    @Column(name = "poi_latitude")
    private Double poiLatitude;

    @Column(name = "poi_longitude")
    private Double poiLongitude;

    @Column(name = "poi_type", length = 100)
    private String poiType;

    @Column(name = "estimated_visit_minutes", nullable = false)
    private Integer estimatedVisitMinutes = 60;

    @Column(name = "planned_arrival_at")
    private LocalDateTime plannedArrivalAt;

    @Column(name = "planned_departure_at")
    private LocalDateTime plannedDepartureAt;

    // Helper methods
    public void setPoiDetails(String name, String address, Double lat, Double lng, String type) {
        this.poiName = name;
        this.poiAddress = address;
        this.poiLatitude = lat;
        this.poiLongitude = lng;
        this.poiType = type;
    }

    public boolean isPoiDetailsLoaded() {
        return poiName != null && poiAddress != null;
    }
}