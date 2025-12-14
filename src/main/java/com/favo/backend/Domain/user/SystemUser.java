package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


//System_user tablosnun gerekli anotasyonlar ile detaylanmış hali

@Entity
@Table(name = "system_user")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
        name = "dtype",
        discriminatorType = DiscriminatorType.STRING,
        length = 30
)
@Getter
@Setter
public abstract class SystemUser extends BaseEntity {

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "username", nullable = false, length = 50)
    private String userName;

    // isActive -> BaseEntity'den geliyor

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_type_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_user_user_type")
    )
    private UserType userType;

    public abstract void deactivate();
}
