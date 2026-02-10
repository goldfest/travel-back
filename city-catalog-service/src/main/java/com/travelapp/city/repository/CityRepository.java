package com.travelapp.city.repository;

import com.travelapp.city.model.entity.City;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<City> findByIsPopularTrue();

    List<City> findByCountryCode(String countryCode);

    Page<City> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<City> findByCountryContainingIgnoreCase(String country, Pageable pageable);

    Page<City> findByIsPopular(Boolean isPopular, Pageable pageable);

    @Query("SELECT c FROM City c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(c.country) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<City> search(@Param("query") String query, Pageable pageable);
}