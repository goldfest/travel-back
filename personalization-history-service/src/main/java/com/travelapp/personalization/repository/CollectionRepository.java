package com.travelapp.personalization.repository;

import com.travelapp.personalization.model.entity.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long> {

    Page<Collection> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    List<Collection> findByUserIdAndNameContainingIgnoreCase(Long userId, String name);

    @Query("SELECT c FROM Collection c WHERE c.userId = :userId AND LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Collection> searchByUserIdAndName(@Param("userId") Long userId,
                                           @Param("searchTerm") String searchTerm,
                                           Pageable pageable);
}