package com.travelapp.route.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "cover_photo_url", length = 500)
    private String coverPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 16)
    private TransportMode transportMode = TransportMode.WALK;

    @Column(name = "is_optimized")
    private Boolean isOptimized = false;

    @Column(name = "optimization_mode")
    private String optimizationMode;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "start_point", length = 300)
    private String startPoint;

    @Column(name = "end_point", length = 300)
    private String endPoint;

    @Column(name = "is_archived")
    private Short isArchived = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteDay> routeDays = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TransportMode {
        WALK, PUBLIC_TRANSPORT, CAR, MIXED
    }

    // Helper methods
    public void addRouteDay(RouteDay routeDay) {
        routeDays.add(routeDay);
        routeDay.setRoute(this);
    }

    public void removeRouteDay(RouteDay routeDay) {
        routeDays.remove(routeDay);
        routeDay.setRoute(null);
    }

    public boolean isArchived() {
        return isArchived != null && isArchived == 1;
    }

    public void archive() {
        this.isArchived = 1;
    }

    public void unarchive() {
        this.isArchived = 0;
    }
}