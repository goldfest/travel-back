package com.travelapp.poi.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "poi_media")
@Getter
@Setter
@ToString
public class PoiMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "media_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    @Column(name = "moderation_status", length = 16)
    @Enumerated(EnumType.STRING)
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = false)
    private Long userId; // User ID from auth service

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_id", nullable = false)
    private Poi poi;

    public enum MediaType {
        PHOTO, COVER, MENU, VIDEO
    }

    public enum ModerationStatus {
        PENDING, APPROVED, REJECTED
    }
}