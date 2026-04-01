package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmailVerificationCode e SET e.consumedAt = :now WHERE e.user.id = :userId AND e.consumedAt IS NULL")
    int consumePendingForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Optional<EmailVerificationCode> findFirstByUser_IdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime now
    );

    Optional<EmailVerificationCode> findTopByUser_IdOrderByCreatedAtDesc(Long userId);
}
