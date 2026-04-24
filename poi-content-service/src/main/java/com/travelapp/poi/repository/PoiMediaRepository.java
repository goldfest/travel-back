package com.travelapp.poi.repository;

import com.travelapp.poi.model.entity.PoiMedia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoiMediaRepository extends JpaRepository<PoiMedia, Long> {

    Optional<PoiMedia> findByIdAndPoiId(Long id, Long poiId);

    List<PoiMedia> findByPoiIdAndModerationStatus(Long poiId, PoiMedia.ModerationStatus moderationStatus);

    Page<PoiMedia> findByModerationStatus(PoiMedia.ModerationStatus moderationStatus, Pageable pageable);
}
