package com.favo.backend.Security;

import com.favo.backend.Domain.user.SystemUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {

    private final SystemUser principal;
    private final String credentials; // token veya uid tutabilirsin

    public FirebaseAuthenticationToken(SystemUser principal,
                                       String credentials,
                                       Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public SystemUser getPrincipal() {
        return principal;
    }
}
