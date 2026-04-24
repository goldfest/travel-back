package com.travelapp.city.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cities")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "center_lat", nullable = false, precision = 10, scale = 8)
    private BigDecimal centerLat;

    @Column(name = "center_lng", nullable = false, precision = 11, scale = 8)
    private BigDecimal centerLng;

    @Column(name = "is_popular")
    private Boolean isPopular = false;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "city_image", joinColumns = @JoinColumn(name = "city_id"))
    @Column(name = "url", nullable = false, length = 500)
    @OrderColumn(name = "sort_order")
    private List<String> imageUrls = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}