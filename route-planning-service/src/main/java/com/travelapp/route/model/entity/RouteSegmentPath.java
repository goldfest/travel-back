package com.travelapp.route.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_segment_paths")
@Getter
@Setter
@NoArgsConstructor
public class RouteSegmentPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_day_id", nullable = false)
    private RouteDay routeDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_route_point_id", nullable = false)
    private RoutePoint fromRoutePoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_route_point_id", nullable = false)
    private RoutePoint toRoutePoint;

    @Column(name = "segment_order", nullable = false)
    private Short segmentOrder;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = "INTERNAL_GRAPH";

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 16)
    private Route.TransportMode transportMode;

    @Column(name = "geometry_source", nullable = false, length = 30)
    private String geometrySource = "GRAPH";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OK";

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "polyline_json", columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String polylineJson = "[]";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
