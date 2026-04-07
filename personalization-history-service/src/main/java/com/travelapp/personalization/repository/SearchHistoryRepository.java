package com.travelapp.personalization.repository;

import com.travelapp.personalization.model.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    Page<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);

    List<SearchHistory> findTop10ByUserIdOrderBySearchedAtDesc(Long userId);

    @Query("SELECT DISTINCT sh.queryText FROM SearchHistory sh " +
            "WHERE sh.userId = :userId AND sh.queryText IS NOT NULL AND sh.queryText != '' " +
            "ORDER BY sh.searchedAt DESC")
    Page<String> findDistinctQueriesByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.searchedAt < :date")
    void deleteOldHistory(@Param("date") LocalDateTime date);

    @Query("SELECT sh FROM SearchHistory sh WHERE sh.userId = :userId AND sh.cityId = :cityId " +
            "ORDER BY sh.searchedAt DESC")
    Page<SearchHistory> findByUserIdAndCityId(@Param("userId") Long userId,
                                              @Param("cityId") Long cityId,
                                              Pageable pageable);
}