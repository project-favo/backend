package com.favo.backend.Domain.notification.Repository;

import com.favo.backend.Domain.notification.PushDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    Optional<PushDeviceToken> findByToken(String token);

    List<PushDeviceToken> findByUserIdAndIsActiveTrue(Long userId);
}
