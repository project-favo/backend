package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "user_type")
@Getter
@Setter
public class UserType extends BaseEntity {


    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
    // Örnek: ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR
}