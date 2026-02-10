package com.travelapp.review.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports", schema = "review_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType;

    @Column(length = 1000)
    private String comment;

    @Column(length = 16)
    @Builder.Default
    private String status = "pending";

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @CreationTimestamp
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
    @PreUpdate
    private void validateReport() {
        if ((reviewId == null && poiId == null) || (reviewId != null && poiId != null)) {
            throw new IllegalArgumentException("Report must target either a review or a POI, but not both or none");
        }

        if (!status.equals("pending") && !status.equals("approved") && !status.equals("rejected")) {
            throw new IllegalArgumentException("Invalid report status");
        }

        if (handledByUserId != null && handledAt == null) {
            handledAt = LocalDateTime.now();
        }
    }
}