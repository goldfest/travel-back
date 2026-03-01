// notification-service/src/main/java/com/travelapp/notification/repository/NotificationRepository.java
package com.travelapp.notification.repository;

import com.travelapp.notification.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsRead(Long userId, Boolean isRead, Pageable pageable);

    Page<Notification> findByUserIdAndType(Long userId, String type, Pageable pageable);

    Page<Notification> findByUserIdAndTypeAndIsRead(Long userId, String type, Boolean isRead, Pageable pageable);

    List<Notification> findByIsReadFalseAndScheduledAtBefore(LocalDateTime dateTime);

    List<Notification> findByUserIdAndIsReadFalse(Long userId);

    Long countByUserIdAndIsReadFalse(Long userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT n.type, COUNT(n) FROM Notification n WHERE n.userId = :userId GROUP BY n.type")
    List<Object[]> countByUserIdGroupByType(@Param("userId") Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.scheduledAt IS NOT NULL AND n.scheduledAt <= :now AND (n.sentAt IS NULL OR n.sentAt < n.scheduledAt)")
    List<Notification> findScheduledNotificationsReadyForSending(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);
    @Query("SELECT n FROM Notification n " +
            "WHERE n.scheduledAt IS NOT NULL AND n.scheduledAt <= :now AND n.sentAt IS NULL")
    List<Notification> findScheduledReady(@Param("now") LocalDateTime now);
}