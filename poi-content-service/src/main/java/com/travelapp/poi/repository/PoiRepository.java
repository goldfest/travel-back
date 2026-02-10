package com.travelapp.poi.repository;

import com.travelapp.poi.model.entity.Poi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PoiRepository extends JpaRepository<Poi, Long>, JpaSpecificationExecutor<Poi> {

    Optional<Poi> findBySlug(String slug);

    Optional<Poi> findByIdAndCityId(Long id, Long cityId);

    List<Poi> findByCityId(Long cityId);

    Page<Poi> findByCityId(Long cityId, Pageable pageable);

    Page<Poi> findByCityIdAndPoiTypeId(Long cityId, Long poiTypeId, Pageable pageable);

    Page<Poi> findByCityIdAndIsVerifiedTrue(Long cityId, Pageable pageable);

    @Query("SELECT p FROM Poi p WHERE p.cityId = :cityId AND p.isVerified = true " +
            "AND (:searchQuery IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchQuery, '%')))")
    Page<Poi> searchByCityAndQuery(@Param("cityId") Long cityId,
                                   @Param("searchQuery") String searchQuery,
                                   Pageable pageable);

    @Query(value = "SELECT * FROM poi p " +
            "WHERE p.city_id = :cityId " +
            "AND p.is_verified = true " +
            "AND p.is_closed = false " +
            "AND ST_DWithin(ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography, " +
            "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius * 1000)",
            nativeQuery = true)
    List<Poi> findNearby(@Param("cityId") Long cityId,
                         @Param("lat") BigDecimal lat,
                         @Param("lng") BigDecimal lng,
                         @Param("radius") Integer radiusKm);

    boolean existsBySlugAndIdNot(String slug, Long id);

    long countByCityId(Long cityId);

    long countByPoiTypeId(Long poiTypeId);

    @Query("SELECT COUNT(p) FROM Poi p WHERE p.isVerified = false")
    long countUnverified();

    @Query("SELECT p FROM Poi p WHERE p.isVerified = false ORDER BY p.createdAt DESC")
    Page<Poi> findUnverified(Pageable pageable);
}