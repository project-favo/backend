package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "system_user")
@Inheritance(strategy = InheritanceType.JOINED) // veya SINGLE_TABLE ikisi de olur

public abstract class SystemUser extends BaseEntity {

    private String userName;
    private String email;
    private String passWordHash;
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_type_id")
    private UserType userType;
}