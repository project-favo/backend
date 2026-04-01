package com.favo.backend.Domain.chat.Repository;

import com.favo.backend.Domain.chat.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findByOwnerIdAndIsActiveTrueOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
}
