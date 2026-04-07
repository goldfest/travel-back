package com.travelapp.route.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "city_graph_versions")
@Getter
@Setter
@NoArgsConstructor
public class CityGraphVersion {

    public enum Status {
        DRAFT,
        ACTIVE,
        ARCHIVED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "source", nullable = false, length = 30)
    private String source = "OSM";

    @Column(name = "bbox_min_lat")
    private Double bboxMinLat;

    @Column(name = "bbox_min_lng")
    private Double bboxMinLng;

    @Column(name = "bbox_max_lat")
    private Double bboxMaxLat;

    @Column(name = "bbox_max_lng")
    private Double bboxMaxLng;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
