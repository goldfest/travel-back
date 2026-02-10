package com.travelapp.poi.repository;

import com.travelapp.poi.model.entity.DataImportTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DataImportTaskRepository extends JpaRepository<DataImportTask, Long> {

    Page<DataImportTask> findByStatus(DataImportTask.ImportStatus status, Pageable pageable);

    Page<DataImportTask> findBySourceCode(String sourceCode, Pageable pageable);

    Page<DataImportTask> findByCityId(Long cityId, Pageable pageable);

    @Query("SELECT t FROM DataImportTask t WHERE t.status = 'RUNNING' AND t.startedAt < :cutoffTime")
    List<DataImportTask> findStalledTasks(LocalDateTime cutoffTime);

    @Query("SELECT COUNT(t) FROM DataImportTask t WHERE t.status = 'RUNNING'")
    long countRunningTasks();
}