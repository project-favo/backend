package com.favo.backend.Domain.message;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.GeneralUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "conversation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_participants",
                        columnNames = {"participant1_id", "participant2_id"}
                )
        }
)
@Getter
@Setter
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "participant1_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversation_participant1")
    )
    private GeneralUser participant1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "participant2_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversation_participant2")
    )
    private GeneralUser participant2;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}

