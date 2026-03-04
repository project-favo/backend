package com.favo.backend.Domain.message.Repository;

import com.favo.backend.Domain.message.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    Long countByConversationIdAndIsReadFalseAndSenderIdNot(Long conversationId, Long currentUserId);

    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.isActive = true " +
           "AND m.isRead = false " +
           "AND m.sender.id <> :currentUserId " +
           "AND (m.conversation.participant1.id = :currentUserId OR m.conversation.participant2.id = :currentUserId)")
    Long countTotalUnreadForUser(@Param("currentUserId") Long currentUserId);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Message m SET m.isRead = true " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.sender.id <> :currentUserId " +
           "AND m.isRead = false " +
           "AND m.isActive = true")
    void markAsReadForConversationAndUser(@Param("conversationId") Long conversationId,
                                          @Param("currentUserId") Long currentUserId);
}

