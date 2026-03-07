package com.travelapp.review.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports", schema = "review_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType;

    @Column(length = 1000)
    private String comment;

    @Column(name = "moderator_comment", length = 1000)
    private String moderatorComment;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "handled_by_user_id")
    private Long handledByUserId;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "poi_id")
    private Long poiId;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null || status.isBlank()) {
            status = "pending";
        }
    }
}