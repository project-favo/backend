package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemUserRepository extends JpaRepository<SystemUser,Long> {

    //Firebase Authentication

    Optional<SystemUser> findByFirebaseUid(String firebaseUid);
    boolean existsByFirebaseUid(String firebaseUid);


    //user lookup
    Optional<SystemUser> findByEmail(String email);
    boolean existedByEmail(String email);


    //Status

    //aktif kullanıcıları çekmek için olan listeleme şekli
    Optional<SystemUser> findByIdAndIsActiveTrue(Long id);

    //login sırasında kullanıcının inaktif olup olmadığını check etmek için olan kısım
    Optional<SystemUser> findByFirebaseUidAndIsActiveTrue(String firebaseUid);
}
