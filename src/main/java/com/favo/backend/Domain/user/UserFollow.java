package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A {@link GeneralUser} following another {@link GeneralUser} (social graph).
 */
@Entity
@Table(
        name = "user_follow",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_follow_pair", columnNames = {"follower_id", "followee_id"})
)
@Getter
@Setter
public class UserFollow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_follow_follower"))
    private GeneralUser follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_follow_followee"))
    private GeneralUser followee;
}
