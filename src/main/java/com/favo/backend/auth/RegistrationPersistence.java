package com.favo.backend.auth;

import com.favo.backend.auth.entity.PendingRegistration;
import com.favo.backend.auth.entity.PendingRegistrationStatus;
import com.favo.backend.auth.repository.PendingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Kısa @Transactional sınırları: pending satırı INSERT (hash yok) ve ayrı UPDATE (hash) için ayrı metodlar
 * (uzun kilit yok, mail gönderimi bu metodların dışında).
 */
@Service
@RequiredArgsConstructor
public class RegistrationPersistence {

    private final PendingRegistrationRepository pendingRegistrationRepository;

    @Transactional
    public int deletePendingForFirebase(String firebaseUid) {
        return pendingRegistrationRepository.deleteByFirebaseUidAndStatusPending(firebaseUid);
    }

    @Transactional
    public PendingRegistration createPendingShell(
            String firebaseUid,
            String emailNormalized,
            String userName,
            String displayName,
            Instant expiresAt) {
        PendingRegistration p = PendingRegistration.builder()
                .firebaseUid(firebaseUid)
                .email(emailNormalized)
                .userName(userName)
                .displayName(displayName)
                .verificationCodeHash(null)
                .expiresAt(expiresAt)
                .attemptCount(0)
                .status(PendingRegistrationStatus.PENDING)
                .build();
        return pendingRegistrationRepository.save(p);
    }

    @Transactional
    public void applyOtpHash(long pendingId, String verificationCodeHash) {
        PendingRegistration p = pendingRegistrationRepository.findById(pendingId)
                .orElseThrow(() -> new IllegalStateException("Pending registration id=" + pendingId + " not found after insert"));
        p.setVerificationCodeHash(verificationCodeHash);
        pendingRegistrationRepository.save(p);
    }
}
