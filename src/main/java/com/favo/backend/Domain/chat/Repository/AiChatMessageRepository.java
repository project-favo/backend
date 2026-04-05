package com.favo.backend.Domain.chat.Repository;

import com.favo.backend.Domain.chat.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /** Genel Favo asistanı (ürün bağlamı olmayan mesajlar). */
    List<AiChatMessage> findByOwnerIdAndProductIsNullAndIsActiveTrueOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    /** Belirli bir ürün için kullanıcıya özel sohbet geçmişi. */
    List<AiChatMessage> findByOwnerIdAndProduct_IdAndIsActiveTrueOrderByCreatedAtDesc(Long ownerId, Long productId, Pageable pageable);
}
