package com.travelapp.route.repository;

import com.travelapp.route.model.entity.PoiGraphBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PoiGraphBindingRepository extends JpaRepository<PoiGraphBinding, Long> {
    List<PoiGraphBinding> findByPoiIdIn(Collection<Long> poiIds);
}
