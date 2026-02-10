package com.travelapp.personalization.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, length = 255)
    private String queryText;

    @CreatedDate
    @Column(name = "searched_at", nullable = false, updatable = false)
    private LocalDateTime searchedAt;

    @Column(columnDefinition = "jsonb")
    private String filtersJson;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @Column(name = "preset_filter_id")
    private Long presetFilterId;
}