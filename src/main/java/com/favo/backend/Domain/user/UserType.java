package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name="user_type")
public class UserType extends BaseEntity {

    private String name;

    @OneToMany(mappedBy = "userType")
    private List<SystemUser> users = new ArrayList<>();
}
