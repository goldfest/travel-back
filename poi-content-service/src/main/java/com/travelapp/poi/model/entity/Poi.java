package com.travelapp.poi.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "poi")
@Getter
@Setter
@ToString(exclude = {"features", "hours", "media", "sources"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Poi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Type(JsonType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private JsonNode tags;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "site_url", length = 300)
    private String siteUrl;

    @Column(name = "price_level")
    private Short priceLevel; // 0-4

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "is_closed")
    private Boolean isClosed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy; // User ID from auth service

    @Column(name = "city_id", nullable = false)
    private Long cityId; // City ID from city service

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_type_id", nullable = false)
    @JsonIgnore
    private PoiType poiType;

    @JsonProperty("poiTypeCode")
    public String getPoiTypeCode() {
        return poiType != null ? poiType.getCode() : null;
    }

    @JsonProperty("poiTypeName")
    public String getPoiTypeName() {
        return poiType != null ? poiType.getName() : null;
    }

    @OneToMany(mappedBy = "poi", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PoiFeature> features = new HashSet<>();

    @OneToMany(mappedBy = "poi", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PoiHours> hours = new HashSet<>();

    @OneToMany(mappedBy = "poi", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PoiMedia> media = new HashSet<>();

    @OneToMany(mappedBy = "poi", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PoiSource> sources = new HashSet<>();

    // Helper method to add feature
    public void addFeature(PoiFeature feature) {
        features.add(feature);
        feature.setPoi(this);
    }

    // Helper method to add hours
    public void addHours(PoiHours poiHours) {
        hours.add(poiHours);
        poiHours.setPoi(this);
    }

    // Helper method to add media
    public void addMedia(PoiMedia poiMedia) {
        media.add(poiMedia);
        poiMedia.setPoi(this);
    }

    // Helper method to add source
    public void addSource(PoiSource source) {
        sources.add(source);
        source.setPoi(this);
    }

    // Update rating statistics
    public void updateRating(BigDecimal newRating) {
        if (ratingCount == null) ratingCount = 0;
        if (averageRating == null) averageRating = BigDecimal.ZERO;

        BigDecimal totalRating = averageRating.multiply(BigDecimal.valueOf(ratingCount))
                .add(newRating);

        ratingCount++;
        averageRating = totalRating.divide(BigDecimal.valueOf(ratingCount), 2, BigDecimal.ROUND_HALF_UP);
    }
}