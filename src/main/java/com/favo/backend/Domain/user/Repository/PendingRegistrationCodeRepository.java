package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.PendingRegistrationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingRegistrationCodeRepository extends JpaRepository<PendingRegistrationCode, Long> {

    /** Cooldown kontrolü için en son kaydı getirir. */
    Optional<PendingRegistrationCode> findTopByEmailOrderByCreatedAtDesc(String email);

    /** Doğrulanmamış, süresi dolmamış en son kodu getirir (kod eşleştirme için). */
    Optional<PendingRegistrationCode> findTopByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    /** Doğrulanmış, kullanılmamış, süresi dolmamış kod var mı? (register kontrolü için) */
    Optional<PendingRegistrationCode> findTopByEmailAndVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    /** E-posta adresine ait tüm bekleyen kodları tüket. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PendingRegistrationCode p SET p.consumedAt = :now WHERE p.email = :email AND p.consumedAt IS NULL")
    int consumePendingForEmail(@Param("email") String email, @Param("now") LocalDateTime now);
}
