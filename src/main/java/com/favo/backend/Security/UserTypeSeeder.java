package com.favo.backend.Security;

import com.favo.backend.Domain.user.Repository.UserTypeRepository;
import com.favo.backend.Domain.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Uygulama ayağa kalkarken user_type tablosuna ROLE_USER ve ROLE_ADMIN ekler (yoksa).
 * RBAC için gerekli.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class UserTypeSeeder implements ApplicationRunner {

    private final UserTypeRepository userTypeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfMissing(SecurityRoles.ROLE_USER);
        seedIfMissing(SecurityRoles.ROLE_ADMIN);
    }

    private void seedIfMissing(String roleName) {
        if (userTypeRepository.findByName(roleName).isEmpty()) {
            UserType type = new UserType();
            type.setName(roleName);
            type.setCreatedAt(LocalDateTime.now());
            type.setIsActive(true);
            userTypeRepository.save(type);
            log.info("UserType seeded: {}", roleName);
        }
    }
}
