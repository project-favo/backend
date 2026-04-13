package com.favo.backend.Domain.notification.Repository;

import com.favo.backend.Domain.notification.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    Page<InAppNotification> findByRecipientIdAndIsActiveTrueOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNullAndIsActiveTrue(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InAppNotification n SET n.readAt = :readAt WHERE n.id = :id AND n.recipient.id = :recipientId AND n.isActive = true")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InAppNotification n SET n.readAt = :readAt WHERE n.recipient.id = :recipientId AND n.readAt IS NULL AND n.isActive = true")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InAppNotification n SET n.isActive = false WHERE n.id = :id AND n.recipient.id = :recipientId AND n.isActive = true")
    int softDelete(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE InAppNotification n SET n.isActive = false WHERE n.recipient.id = :recipientId AND n.isActive = true")
    int softDeleteAll(@Param("recipientId") Long recipientId);
}
