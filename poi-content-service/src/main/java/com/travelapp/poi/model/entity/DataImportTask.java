package com.travelapp.poi.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_import_task")
@Getter
@Setter
@ToString
public class DataImportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, length = 32)
    private String sourceCode;

    @Column(name = "query", nullable = false, length = 255)
    private String query;

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "total_poi_found")
    private Integer totalPoiFound = 0;

    @Column(name = "total_poi_created")
    private Integer totalPoiCreated = 0;

    @Column(name = "total_poi_updated")
    private Integer totalPoiUpdated = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "city_id")
    private Long cityId; // City ID from city service

    public enum ImportStatus {
        PENDING, RUNNING, SUCCESS, FAILED
    }

    // Helper methods
    public void start() {
        this.status = ImportStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(int created, int updated) {
        this.status = ImportStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
        this.totalPoiCreated = created;
        this.totalPoiUpdated = updated;
    }

    public void fail(String error) {
        this.status = ImportStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = error;
    }
}