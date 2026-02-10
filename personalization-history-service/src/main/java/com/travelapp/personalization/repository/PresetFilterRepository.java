package com.travelapp.personalization.repository;

import com.travelapp.personalization.model.entity.PresetFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresetFilterRepository extends JpaRepository<PresetFilter, Long> {

    Page<PresetFilter> findByUserId(Long userId, Pageable pageable);

    List<PresetFilter> findByUserIdAndCityIdAndPoiTypeId(Long userId, Long cityId, Long poiTypeId);

    Optional<PresetFilter> findByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndName(Long userId, String name);

    @Query("SELECT pf FROM PresetFilter pf WHERE pf.userId = :userId AND " +
            "(:cityId IS NULL OR pf.cityId = :cityId) AND " +
            "(:poiTypeId IS NULL OR pf.poiTypeId = :poiTypeId)")
    Page<PresetFilter> findWithFilters(@Param("userId") Long userId,
                                       @Param("cityId") Long cityId,
                                       @Param("poiTypeId") Long poiTypeId,
                                       Pageable pageable);

    void deleteByUserId(Long userId);
}