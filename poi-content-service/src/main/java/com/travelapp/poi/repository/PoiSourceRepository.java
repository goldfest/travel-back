package com.travelapp.poi.repository;

import com.travelapp.poi.model.entity.PoiSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PoiSourceRepository extends JpaRepository<PoiSource, Long> {

    Optional<PoiSource> findFirstBySourceCodeAndExternalId(String sourceCode, String externalId);

    Optional<PoiSource> findFirstBySourceCodeAndSourceUrl(String sourceCode, String sourceUrl);
}