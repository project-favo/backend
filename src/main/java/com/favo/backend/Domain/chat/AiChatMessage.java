package com.favo.backend.Domain.chat;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_chat_message")
@Getter
@Setter
public class AiChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ai_chat_message_owner"))
    private SystemUser owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AiChatRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}
