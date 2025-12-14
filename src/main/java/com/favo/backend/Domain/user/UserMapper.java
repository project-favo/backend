package com.favo.backend.Domain.user;

public class UserMapper {
    public static UserResponseDto toDto(SystemUser user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.getUserType().getName(),
                Boolean.TRUE.equals(user.getIsActive())
        );
    }
}
