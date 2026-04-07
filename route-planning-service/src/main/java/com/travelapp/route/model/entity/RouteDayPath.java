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
@Table(name = "route_day_paths")
@Getter
@Setter
@NoArgsConstructor
public class RouteDayPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_day_id", nullable = false, unique = true)
    private RouteDay routeDay;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = "INTERNAL_GRAPH";

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 16)
    private Route.TransportMode transportMode;

    @Column(name = "geometry_source", nullable = false, length = 30)
    private String geometrySource = "GRAPH";

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

    @Column(name = "built_at")
    private LocalDateTime builtAt;
}
