package com.favo.backend.auth;

import lombok.Value;

@Value
public class UserResponseDto {
    Long id;
    String email;
    String userName;
    String displayName;
    String userType;
    boolean active;
    boolean emailVerified;
    String profileImageUrl;
}
