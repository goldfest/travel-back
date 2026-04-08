package com.travelapp.route.repository;

import com.travelapp.route.model.entity.CityGraphVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CityGraphVersionRepository extends JpaRepository<CityGraphVersion, Long> {

    Optional<CityGraphVersion> findFirstByCityIdAndStatusOrderByVersionNoDesc(Long cityId, CityGraphVersion.Status status);

    Optional<CityGraphVersion> findFirstByCityIdOrderByVersionNoDesc(Long cityId);

    @Query("select coalesce(max(c.versionNo), 0) from CityGraphVersion c where c.cityId = :cityId")
    int findMaxVersionNo(@Param("cityId") Long cityId);

    @Modifying
    @Query("update CityGraphVersion c set c.status = 'ARCHIVED' where c.cityId = :cityId and c.status = 'ACTIVE'")
    void archiveActiveByCityId(@Param("cityId") Long cityId);
}
