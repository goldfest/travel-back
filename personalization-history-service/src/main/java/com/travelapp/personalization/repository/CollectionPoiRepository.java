package com.travelapp.personalization.repository;

import com.travelapp.personalization.model.entity.CollectionPoi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionPoiRepository extends JpaRepository<CollectionPoi, Long> {

    Optional<CollectionPoi> findByCollectionIdAndPoiId(Long collectionId, Long poiId);

    boolean existsByCollectionIdAndPoiId(Long collectionId, Long poiId);

    Page<CollectionPoi> findByCollectionId(Long collectionId, Pageable pageable);

    List<CollectionPoi> findByCollectionIdOrderByOrderIndexAsc(Long collectionId);

    @Query("SELECT cp FROM CollectionPoi cp WHERE cp.collectionId = :collectionId AND cp.poiId = :poiId")
    Optional<CollectionPoi> findInCollection(@Param("collectionId") Long collectionId,
                                             @Param("poiId") Long poiId);

    @Query("SELECT cp.poiId FROM CollectionPoi cp WHERE cp.collectionId = :collectionId ORDER BY cp.orderIndex ASC")
    List<Long> findPoiIdsByCollectionId(@Param("collectionId") Long collectionId);

    @Query("SELECT COUNT(cp) FROM CollectionPoi cp WHERE cp.collectionId = :collectionId")
    long countByCollectionId(@Param("collectionId") Long collectionId);

    @Modifying
    @Query("DELETE FROM CollectionPoi cp WHERE cp.collectionId = :collectionId AND cp.poiId = :poiId")
    void deleteFromCollection(@Param("collectionId") Long collectionId,
                              @Param("poiId") Long poiId);

    @Modifying
    @Query("DELETE FROM CollectionPoi cp WHERE cp.collectionId = :collectionId")
    void deleteAllByCollectionId(@Param("collectionId") Long collectionId);

    @Modifying
    @Query("UPDATE CollectionPoi cp SET cp.orderIndex = :orderIndex WHERE cp.id = :id")
    void updateOrderIndex(@Param("id") Long id,
                          @Param("orderIndex") Integer orderIndex);
}