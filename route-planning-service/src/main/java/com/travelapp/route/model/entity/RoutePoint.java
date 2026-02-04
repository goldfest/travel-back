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

    @Transient
    private String poiName;

    @Transient
    private String poiAddress;

    @Transient
    private Double poiLatitude;

    @Transient
    private Double poiLongitude;

    @Transient
    private String poiType;

    @Transient
    private Integer estimatedDuration;

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