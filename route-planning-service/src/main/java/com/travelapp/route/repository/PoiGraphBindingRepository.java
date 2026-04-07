package com.travelapp.route.repository;

import com.travelapp.route.model.entity.PoiGraphBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PoiGraphBindingRepository extends JpaRepository<PoiGraphBinding, Long> {
    List<PoiGraphBinding> findByPoiIdInAndGraphVersionId(Collection<Long> poiIds, Long graphVersionId);
    Optional<PoiGraphBinding> findByPoiIdAndGraphVersionId(Long poiId, Long graphVersionId);
}
