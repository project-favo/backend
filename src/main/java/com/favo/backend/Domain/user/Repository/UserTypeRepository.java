package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.UserType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTypeRepository
        extends JpaRepository<UserType, Long> {

    Optional<UserType> findByName(String name);
}
