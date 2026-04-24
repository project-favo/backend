package com.favo.backend.auth.repository;

import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration-specific username resolution over {@code system_user} and {@code pending_registrations}.
 * <p>
 * <b>DB stratejisi</b>: aktif kullanıcı kontrolü Spring Data
 * {@code existsByUserNameIgnoreCaseAndIsActiveTrue} ile JPQL (
 * LOWER(?) eşleşmesi, provider'a göre) gerçekleşir. İsteğe bağlı iyileştirme: {@code LOWER(userName)}
 * sütununda veya MySQL 8'de bu sütun üzerinde B-tree indeks.
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final SystemUserRepository systemUserRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;

    @Transactional(readOnly = true)
    public boolean isUsernameTakenInUsersOrPending(String userName) {
        boolean inUsers = systemUserRepository.existsByUserNameIgnoreCaseAndIsActiveTrue(userName);
        boolean inPending = pendingRegistrationRepository.existsPendingByUserNameIgnoreCase(userName);
        return inUsers || inPending;
    }
}
