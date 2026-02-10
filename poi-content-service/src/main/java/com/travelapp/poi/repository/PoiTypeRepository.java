package com.travelapp.poi.repository;

import com.travelapp.poi.model.entity.PoiType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoiTypeRepository extends JpaRepository<PoiType, Long> {

    Optional<PoiType> findByCode(String code);

    List<PoiType> findByCodeIn(List<String> codes);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}