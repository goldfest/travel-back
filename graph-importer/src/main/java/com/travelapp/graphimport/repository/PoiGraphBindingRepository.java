package com.travelapp.graphimport.repository;

import com.travelapp.graphimport.model.entity.PoiGraphBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoiGraphBindingRepository extends JpaRepository<PoiGraphBinding, Long> {
    @Modifying
    @Query("delete from PoiGraphBinding p where p.cityId = :cityId and p.graphVersion.id = :graphVersionId")
    void deleteByCityIdAndGraphVersionId(@Param("cityId") Long cityId, @Param("graphVersionId") Long graphVersionId);
}
