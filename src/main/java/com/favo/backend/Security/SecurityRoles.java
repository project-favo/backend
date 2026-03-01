package com.favo.backend.Security;

/**
 * RBAC: Spring Security authority isimleri.
 * UserType.name ile eşleşir; hasRole("ADMIN") → ROLE_ADMIN authority aranır.
 */
public final class SecurityRoles {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private SecurityRoles() {}
}
