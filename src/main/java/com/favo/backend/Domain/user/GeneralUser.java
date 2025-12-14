package com.favo.backend.Domain.user;


import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("GENERAL_USER")
public class GeneralUser extends SystemUser {

    @Override
    public void deactivate() {
        setIsActive(false);
    }
}
