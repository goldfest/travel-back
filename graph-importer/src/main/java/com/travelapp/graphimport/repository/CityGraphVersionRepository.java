package com.travelapp.graphimport.repository;

import com.travelapp.graphimport.model.entity.CityGraphVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CityGraphVersionRepository extends JpaRepository<CityGraphVersion, Long> {

    Optional<CityGraphVersion> findFirstByCityIdAndStatusOrderByVersionNoDesc(Long cityId, CityGraphVersion.Status status);

    @Query("select coalesce(max(c.versionNo), 0) from CityGraphVersion c where c.cityId = :cityId")
    int findMaxVersionNo(@Param("cityId") Long cityId);

    @Modifying
    @Query("update CityGraphVersion c set c.status = 'ARCHIVED' where c.cityId = :cityId and c.status = 'ACTIVE'")
    void archiveActiveByCityId(@Param("cityId") Long cityId);

    @Query("select c from CityGraphVersion c where c.cityId = :cityId and c.status = 'ARCHIVED' order by c.versionNo desc")
    List<CityGraphVersion> findArchivedByCityIdOrderByVersionNoDesc(@Param("cityId") Long cityId);
}
