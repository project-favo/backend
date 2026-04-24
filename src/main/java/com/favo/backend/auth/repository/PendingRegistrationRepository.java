package com.favo.backend.auth.repository;

import com.favo.backend.auth.entity.PendingRegistration;
import com.favo.backend.auth.entity.PendingRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /**
     * Case-insensitive match on stored lowercase email.
     */
    @Query("SELECT p FROM PendingRegistration p WHERE LOWER(p.email) = LOWER(:email) AND p.status = :status")
    Optional<PendingRegistration> findByEmailLowerAndStatus(
            @Param("email") String email, @Param("status") PendingRegistrationStatus status);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PendingRegistration p "
            + "WHERE LOWER(p.userName) = LOWER(:userName) AND p.status = 'PENDING'")
    boolean existsPendingByUserNameIgnoreCase(@Param("userName") String userName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PendingRegistration p where p.firebaseUid = :uid and p.status = 'PENDING'")
    int deleteByFirebaseUidAndStatusPending(@Param("uid") String uid);
}
