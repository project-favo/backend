package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.SystemUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SystemUserRepository
        extends JpaRepository<SystemUser, Long> {

    Optional<SystemUser> findByFirebaseUid(String firebaseUid);

    /**
     * E-posta doğrulandığında kalıcı güncelleme (entity merge yerine doğrudan UPDATE; flush/clear ile tutarlı okuma).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SystemUser u SET u.emailVerified = true WHERE u.id = :userId")
    int markEmailVerifiedTrue(@Param("userId") Long userId);

    /**
     * Firebase UID'ye göre aktif kullanıcıyı UserType ile birlikte getirir (N+1 query problemini önlemek için)
     * LEFT JOIN FETCH ile UserType tek query'de çekilir
     * 
     * FirebaseAuthenticationFilter içinde user.getUserType().getName() çağrıldığı için
     * UserType'ın eager load edilmesi gerekiyor
     */
    @Query("SELECT DISTINCT u FROM SystemUser u LEFT JOIN FETCH u.userType WHERE u.firebaseUid = :firebaseUid AND u.isActive = true")
    Optional<SystemUser> findByFirebaseUidAndIsActiveTrue(@Param("firebaseUid") String firebaseUid);

    boolean existsByFirebaseUid(String firebaseUid);

    boolean existsByEmail(String email); // 🔥 DOĞRU

    Optional<SystemUser> findByEmail(String email);

    Optional<SystemUser> findByEmailIgnoreCase(String email);

    boolean existsByUserName(String userName);

    /**
     * Username'in aktif kullanıcılar arasında kullanılıp kullanılmadığını kontrol eder
     * Soft delete edilmiş (isActive = false) kullanıcılar kontrol edilmez
     */
    boolean existsByUserNameAndIsActiveTrue(String userName);

    /**
     * ID'ye göre kullanıcıyı UserType ile birlikte getirir (N+1 query problemini önlemek için)
     * LEFT JOIN FETCH ile UserType tek query'de çekilir
     * 
     * /me endpoint'inde user.getUserType().getName() çağrıldığı için
     * UserType'ın eager load edilmesi gerekiyor
     */
    @Query("SELECT DISTINCT u FROM SystemUser u LEFT JOIN FETCH u.userType WHERE u.id = :id AND u.isActive = true")
    Optional<SystemUser> findByIdWithUserType(@Param("id") Long id);

    /** Admin: Tüm kullanıcıları (aktif + pasif) UserType ile sayfalı getirir */
    @Query(value = "SELECT DISTINCT u FROM SystemUser u LEFT JOIN FETCH u.userType ORDER BY u.id",
           countQuery = "SELECT COUNT(u) FROM SystemUser u")
    Page<SystemUser> findAllWithUserType(Pageable pageable);

    /** Admin: Sadece aktif kullanıcıları UserType ile sayfalı getirir */
    @Query(value = "SELECT DISTINCT u FROM SystemUser u LEFT JOIN FETCH u.userType WHERE u.isActive = true ORDER BY u.id",
           countQuery = "SELECT COUNT(u) FROM SystemUser u WHERE u.isActive = true")
    Page<SystemUser> findActiveWithUserType(Pageable pageable);

    /** Admin: ID ile kullanıcı getirir (aktif/pasif fark etmez), UserType ile */
    @Query("SELECT DISTINCT u FROM SystemUser u LEFT JOIN FETCH u.userType WHERE u.id = :id")
    Optional<SystemUser> findByIdWithUserTypeForAdmin(@Param("id") Long id);
}
