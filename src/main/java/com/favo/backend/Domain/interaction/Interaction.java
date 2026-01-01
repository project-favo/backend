package com.favo.backend.Domain.interaction;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.GeneralUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Interaction base entity
 * ERD'ye uygun olarak ayrı bir tablo olarak oluşturulur (JOINED inheritance)
 * ReviewInteraction ve ProductInteraction bu tabloya JOIN ile bağlanır
 */
@Entity
@Table(name = "interaction")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
public abstract class Interaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "performer_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_interaction_performer")
    )
    private GeneralUser performer; // Interaction'ı yapan kullanıcı

    // createdAt -> BaseEntity'den geliyor (created_at column'u)
    // isActive -> BaseEntity'den geliyor (is_active column'u)

    public abstract void recordInteraction();
}
