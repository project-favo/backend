package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemUserRepository
        extends JpaRepository<SystemUser, Long> {

    Optional<SystemUser> findByFirebaseUid(String firebaseUid);

    boolean existsByFirebaseUid(String firebaseUid);

    boolean existsByEmail(String email); // 🔥 DOĞRU

    Optional<SystemUser> findByEmail(String email);

    Optional<SystemUser> findByFirebaseUidAndIsActiveTrue(String firebaseUid);
}
