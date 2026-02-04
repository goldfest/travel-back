package com.travelapp.route.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "route_days")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_number", nullable = false)
    private Short dayNumber;

    @Column(name = "planned_start")
    private LocalDateTime plannedStart;

    @Column(name = "planned_end")
    private LocalDateTime plannedEnd;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @OneToMany(mappedBy = "routeDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<RoutePoint> routePoints = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Helper methods
    public void addRoutePoint(RoutePoint routePoint) {
        routePoints.add(routePoint);
        routePoint.setRouteDay(this);
    }

    public void removeRoutePoint(RoutePoint routePoint) {
        routePoints.remove(routePoint);
        routePoint.setRouteDay(null);
    }

    public LocalTime getEstimatedStartTime() {
        return plannedStart != null ? plannedStart.toLocalTime() : LocalTime.of(9, 0);
    }

    public LocalTime getEstimatedEndTime() {
        return plannedEnd != null ? plannedEnd.toLocalTime() : LocalTime.of(18, 0);
    }

    public int getEstimatedDurationHours() {
        if (plannedStart != null && plannedEnd != null) {
            return plannedEnd.getHour() - plannedStart.getHour();
        }
        return 8; // Default 8 hours
    }
}