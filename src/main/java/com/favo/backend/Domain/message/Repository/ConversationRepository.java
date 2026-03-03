package com.favo.backend.Domain.message.Repository;

import com.favo.backend.Domain.message.Conversation;
import com.favo.backend.Domain.user.GeneralUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByParticipant1AndParticipant2(GeneralUser participant1, GeneralUser participant2);

    Page<Conversation> findByParticipant1IdOrParticipant2Id(Long userId1, Long userId2, Pageable pageable);

    @Query("SELECT c FROM Conversation c " +
           "WHERE (c.participant1.id = :userId1 AND c.participant2.id = :userId2) " +
           "   OR (c.participant1.id = :userId2 AND c.participant2.id = :userId1)")
    Optional<Conversation> findConversationBetweenUsers(@Param("userId1") Long userId1,
                                                        @Param("userId2") Long userId2);
}

