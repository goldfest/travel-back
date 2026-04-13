package com.travelapp.notification.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "poi_id")
    private Long poiId;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "route_day_id")
    private Long routeDayId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_key", length = 120, unique = true)
    private String eventKey;

    @Default
    @Column(name = "delivery_channel", length = 20, nullable = false)
    private String deliveryChannel = "IN_APP";

    @Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Type {
        ROUTE_CREATED("route_created"),
        ROUTE_DAY_START("route_day_start"),
        ROUTE_REMINDER("route_reminder"),
        REVIEW("review"),
        POI_UPDATE("poi_update"),
        MODERATION("moderation"),
        SYSTEM("system"),
        PROMOTION("promotion");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (isRead == null) {
            isRead = false;
        }
        if (deliveryChannel == null || deliveryChannel.isBlank()) {
            deliveryChannel = "IN_APP";
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public void markAsSent() {
        this.sentAt = LocalDateTime.now();
        this.status = "SENT";
    }

    public void markAsFailed() {
        this.status = "FAILED";
    }
}