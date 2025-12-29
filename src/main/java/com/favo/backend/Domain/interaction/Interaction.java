package com.favo.backend.Domain.interaction;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.GeneralUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
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

    // createDate -> BaseEntity'den createdAt olarak geliyor

    public abstract void recordInteraction();
}
