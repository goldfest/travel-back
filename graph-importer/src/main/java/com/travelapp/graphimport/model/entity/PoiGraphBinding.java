package com.travelapp.graphimport.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "poi_graph_bindings")
@Getter
@Setter
@NoArgsConstructor
public class PoiGraphBinding {

    @Id
    @Column(name = "poi_id")
    private Long poiId;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graph_version_id", nullable = false)
    private CityGraphVersion graphVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nearest_node_id", nullable = false)
    private RoadNode nearestNode;

    @Column(name = "snapped_latitude")
    private Double snappedLatitude;

    @Column(name = "snapped_longitude")
    private Double snappedLongitude;

    @Column(name = "snap_distance_m")
    private Double snapDistanceM;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
